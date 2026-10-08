import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar
import org.gradle.api.tasks.testing.Test
import java.io.IOException
import java.util.zip.ZipFile

plugins {
    // Applies the correct Loom variant for the active Minecraft version.
    id("dev.kikugie.loom-back-compat")
}

val baseModVersion = providers.gradleProperty("mod.version").get()
val buildNumber = providers.gradleProperty("build.number").orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
val effectiveModVersion = buildNumber?.let { "$baseModVersion-build.$it" } ?: baseModVersion
val exportBuildSuffix = buildNumber?.let { "-build.$it" } ?: ""
val archiveName = providers.gradleProperty("mod.archive_name").get()
val exportJarName = "${archiveName}-v$baseModVersion-mc${sc.current.version}$exportBuildSuffix.jar"

// DO NOT set group directly; each Stonecutter version supplies it from its gradle.properties.
version = "$effectiveModVersion+${sc.current.version}"
base.archivesName = archiveName

val requiredJava = if (sc.current.parsed >= "26.1") {
    JavaVersion.VERSION_25
} else {
    JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven("https://api.modrinth.com/maven") { name = "Modrinth" }
    maven("https://jitpack.io") { name = "JitPack" }
    maven("https://maven.fallenbreath.me/releases") { name = "FallenBreath" }
    maven("https://maven.bawnorton.com/releases") { name = "Bawnorton" }
    maven("https://maven.shedaniel.me/")
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()


    val lwjglVersion = if (sc.current.parsed >= "26.1") "3.4.1" else "3.3.3"
//    runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion")

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    val fabricApiVersion = property("deps.fabric_api") as String
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    modImplementation("maven.modrinth:flashback:${property("deps.flashback")}-fabric,${sc.current.version}")

    // Runtime restrictions for optional compatibility Mixins. Bundle the
    // small library so end users do not need to install another mod.
    val conditionalMixin = "me.fallenbreath:conditional-mixin-fabric:0.6.4"
    modImplementation(conditionalMixin)
    include(conditionalMixin)

    // Optional compatibility targets: available to the compiler and local
    // development runtime, but never declared as production requirements.
    modCompileOnly("maven.modrinth:sodium:${property("deps.sodium")}")
    modLocalRuntime("maven.modrinth:sodium:${property("deps.sodium")}")
    modCompileOnly("maven.modrinth:iris:${property("deps.iris")}")
    modLocalRuntime("maven.modrinth:iris:${property("deps.iris")}")

    // Iris ships JCPP as a nested jar. Loom's development runtime does not
    // expose nested mod jars on the runClient classpath, so provide the same
    // library explicitly for shaderpack parsing. This is runtime-only and is
    // not bundled into Flashback Export Extras or declared as a mod dependency.
    runtimeOnly("org.anarres:jcpp:1.4.14")
    // Iris also embeds these shader transformation libraries. Loom does not
    // put nested Iris jars on runClient's classpath, so expose them explicitly
    // for development without adding them to the produced mod.
    runtimeOnly("io.github.douira:glsl-transformer:3.0.0-pre3")
    runtimeOnly("org.antlr:antlr4-runtime:4.13.1")
    runtimeOnly("org.antlr:antlr4:4.13.1")

    if (sc.current.parsed < "26.1") {
        // Flashback 0.39.x embeds Apache HttpClient, but Loom does not expose
        // that nested library while running the exploded development mod.
        // It is needed by Flashback's export-complete window, not by this mod.
        runtimeOnly("org.apache.httpcomponents:httpclient:4.5.14")
    }

    modLocalRuntime("com.moulberry:mixinconstraints:1.0.8")
    if (sc.current.parsed < "26.1") {
        // Flashback 0.42.x embeds its 26.1-specific Lattice version.
        modLocalRuntime("com.moulberry:lattice:1.3.1")
    }
    modLocalRuntime("com.github.bawnorton.mixinsquared:mixinsquared-fabric:0.3.7-beta.1")

    val hdrMod = findProperty("deps.hdr_mod") as String?
    if (!hdrMod.isNullOrBlank()) {
        // HDR is integrated through an optional Mixin, so its classes are now
        // regular compile/runtime dependencies for HDR-enabled versions.
        modImplementation("maven.modrinth:tycXenOB:$hdrMod")
        modImplementation("me.shedaniel.cloth:cloth-config-fabric:${property("deps.cloth_config")}")
        modImplementation("dev.architectury:architectury-fabric:${property("deps.architectury")}")
    }

    // 26.x ships LWJGL 3.4.1. TinyEXR must use the same binding version as
    // Minecraft's LWJGL core; mixing 3.3.3 TinyEXR with 3.4.1 core fails at
    // runtime with EXRHeader.NoSuchFieldError (Unsafe field layout changed).
    val lwjglNatives = when {
        org.gradle.internal.os.OperatingSystem.current().isMacOsX ->
            if (org.gradle.internal.os.OperatingSystem.current().nativePrefix.contains("aarch64")) {
                "natives-macos-arm64"
            } else {
                "natives-macos"
            }
        org.gradle.internal.os.OperatingSystem.current().isLinux -> "natives-linux"
        else -> "natives-windows"
    }
    val lwjglNativeClassifiers = listOf(
        "natives-windows",
        "natives-linux",
        "natives-macos",
        "natives-macos-arm64"
    )

    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.lwjgl:lwjgl-tinyexr")
    runtimeOnly("org.lwjgl:lwjgl-tinyexr::$lwjglNatives")
    include("org.lwjgl:lwjgl-tinyexr:$lwjglVersion")
    lwjglNativeClassifiers.forEach { classifier ->
        include(dependencies.create("org.lwjgl:lwjgl-tinyexr:$lwjglVersion:$classifier"))
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")
    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        // Each Minecraft version needs an isolated Loom/Fabric runtime state.
        // Sharing `run` causes classTweaker namespace mismatches when switching versions.
        runDirectory = rootProject.file("run/${sc.current.version}")
        jvmArguments.add("-Dmixin.debug.export=true")
    }
}

java {
    withSourcesJar()
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

// Read Stonecutter's version properties before entering the task action.
// The values are plain strings, so the task configuration remains safe for
// Gradle's configuration cache while still seeing properties injected by the
// version subproject.
val minecraftCompatibility = findProperty("mod.mc_compat") as String?
if (minecraftCompatibility != null) {
    val resourceProperties = mapOf(
        "id" to (findProperty("mod.id") as String),
        "name" to (findProperty("mod.name") as String),
        "version" to effectiveModVersion,
        "minecraft" to minecraftCompatibility,
        "loader" to (findProperty("deps.fabric_loader") as String),
        "flashback" to (findProperty("req.flashback") as String)
    )
    val mixinResourceProperties = mapOf(
        "java" to "JAVA_${requiredJava.majorVersion}"
    )

    tasks.withType<ProcessResources>().configureEach {
        inputs.properties(resourceProperties)
        inputs.properties(mixinResourceProperties)

        filesMatching("fabric.mod.json") {
            expand(resourceProperties)
        }

        // The mixin list is fixed; only the Java compatibility placeholder is
        // version-dependent.
        filesMatching("*.mixins.json") {
            expand(mixinResourceProperties)
        }

    }
}

// Distribute the license text with every produced JAR.
tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE.txt"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Instance to deploy to, read from versions/<version>/gradle.properties.
// Surrounding quotes are optional so paths with spaces can be written either way.
val configuredInstanceDirectory = (findProperty("minecraft_instance_dir") as String?)
    .orEmpty()
    .trim()
    .removeSurrounding("\"")
    .removeSurrounding("'")

tasks {

    register("buildAndDeploy") {
        group = "build"
        description = "Builds the ${sc.current.version} jar and deploys it to that version's configured Minecraft instance."
        dependsOn("build")

        // Local copies keep the task action free of script references (configuration cache).
        val instanceDirectory = configuredInstanceDirectory
        val deployModId = providers.gradleProperty("mod.id").get()
        val deployProjectName = project.name
        val modJarFile = loomx.modJar.flatMap { it.archiveFile }
        doLast {
            if (instanceDirectory.isEmpty()) {
                throw GradleException(
                    "Set minecraft_instance_dir in versions/$deployProjectName/gradle.properties before running buildAndDeploy."
                )
            }

            val instanceDir = File(instanceDirectory)
            if (!instanceDir.isDirectory) {
                throw GradleException("Minecraft instance directory does not exist: $instanceDir")
            }

            val modsDir = File(instanceDir, "mods")
            if (!modsDir.exists() && !modsDir.mkdirs()) {
                throw GradleException("Could not create mods directory: $modsDir")
            }

            val jarFile = modJarFile.get().asFile
            val idPattern = Regex("\"id\"\\s*:\\s*\"${Regex.escape(deployModId)}\"")

            // Remove only older jars of this mod, identified by the id in fabric.mod.json.
            modsDir.listFiles { file -> file.isFile && file.extension == "jar" }.orEmpty().forEach { candidate ->
                val belongsToThisMod = try {
                    ZipFile(candidate).use { zip ->
                        zip.getEntry("fabric.mod.json")
                            ?.let { zip.getInputStream(it).readBytes().toString(Charsets.UTF_8) }
                            ?.let { idPattern.containsMatchIn(it) }
                            ?: false
                    }
                } catch (ignored: IOException) {
                    // Not a readable Fabric mod jar; leave it untouched.
                    false
                }
                if (belongsToThisMod && !candidate.delete()) {
                    throw GradleException("Could not replace existing mod jar: $candidate")
                }
            }

            jarFile.copyTo(File(modsDir, jarFile.name), overwrite = true)
            logger.lifecycle("Deployed ${jarFile.name} to $modsDir")
        }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to build/libs/{mod version}/{Minecraft version}/"
        from(loomx.modJar.flatMap { it.archiveFile })
        rename(".*\\.jar", exportJarName)
        into(rootProject.layout.buildDirectory.dir("libs/$effectiveModVersion/${sc.current.version}"))
    }

    named("build") {
        dependsOn("buildAndCollect")
    }
}
