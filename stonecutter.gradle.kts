plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.1"

// True when `version` is at least `minimum` (numeric, dot-separated).
fun versionAtLeast(version: String, minimum: String): Boolean {
    val actual = version.split('.').map { it.toIntOrNull() ?: 0 }
    val required = minimum.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(actual.size, required.size)) {
        val a = actual.getOrElse(i) { 0 }
        val r = required.getOrElse(i) { 0 }
        if (a != r) return a > r
    }
    return true
}

// Blaze3D classes that moved to `com.mojang.renderpearl` in Minecraft 26.3.
val renderpearlClasses = mapOf(
    "com.mojang.blaze3d.GpuFormat" to "com.mojang.renderpearl.api.GpuFormat",
    "com.mojang.blaze3d.PrimitiveTopology" to "com.mojang.renderpearl.api.pipeline.PrimitiveTopology",
    "com.mojang.blaze3d.buffers.GpuBuffer" to "com.mojang.renderpearl.api.buffers.GpuBuffer",
    "com.mojang.blaze3d.buffers.GpuBufferSlice" to "com.mojang.renderpearl.api.buffers.GpuBufferSlice",
    "com.mojang.blaze3d.buffers.GpuFence" to "com.mojang.renderpearl.api.commands.GpuFence",
    "com.mojang.blaze3d.opengl.DirectStateAccess" to "com.mojang.renderpearl.backend.opengl.DirectStateAccess",
    "com.mojang.blaze3d.opengl.GlCommandEncoder" to "com.mojang.renderpearl.backend.opengl.GlCommandEncoder",
    "com.mojang.blaze3d.opengl.GlDevice" to "com.mojang.renderpearl.backend.opengl.GlDevice",
    "com.mojang.blaze3d.opengl.GlRenderPipeline" to "com.mojang.renderpearl.backend.opengl.GlRenderPipeline",
    "com.mojang.blaze3d.opengl.GlTexture" to "com.mojang.renderpearl.backend.opengl.GlTexture",
    "com.mojang.blaze3d.pipeline.BindGroupLayout" to "com.mojang.renderpearl.api.pipeline.BindGroupLayout",
    "com.mojang.blaze3d.pipeline.ColorTargetState" to "com.mojang.renderpearl.api.pipeline.ColorTargetState",
    "com.mojang.blaze3d.pipeline.RenderPipeline" to "com.mojang.renderpearl.api.pipeline.RenderPipeline",
    "com.mojang.blaze3d.shaders.UniformType" to "com.mojang.renderpearl.api.pipeline.UniformType",
    "com.mojang.blaze3d.systems.CommandEncoder" to "com.mojang.renderpearl.api.commands.CommandEncoder",
    "com.mojang.blaze3d.systems.RenderPass" to "com.mojang.renderpearl.api.commands.RenderPass",
    "com.mojang.blaze3d.textures.FilterMode" to "com.mojang.renderpearl.api.textures.FilterMode",
    "com.mojang.blaze3d.textures.GpuTexture" to "com.mojang.renderpearl.api.textures.GpuTexture",
    "com.mojang.blaze3d.textures.GpuTextureView" to "com.mojang.renderpearl.api.textures.GpuTextureView"
)

stonecutter parameters {
    swaps["mod_version"] = "\"${node.project.property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"
    constants["release"] = true
    constants["hdr"] = node.metadata.version !in setOf("1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8")
    constants["legacy_hdr"] = node.metadata.version !in setOf("1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8")
            && node.metadata.version !in setOf("26.1.2", "26.2", "26.3")
    // Flashback 0.43.6 changed InternalExport.selectedVideoEncoder from int[] (index) to String (name).
    constants["flashback_encoder_name"] =
        versionAtLeast(node.project.property("deps.flashback") as String, "0.43.6")
    constants["mc_26_1_2"] = node.metadata.version == "26.1.2"
    dependencies["fapi"] = node.project.property("deps.fabric_api") as String

    replacements {
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }

        // 26.3 moved Blaze3D's GPU abstraction into the `renderpearl` packages.
        string(current.parsed >= "26.3") {
            renderpearlClasses.forEach { (from, to) ->
                replace(from, to)
                replace(from.replace('.', '/'), to.replace('.', '/'))
            }
            // GameRenderer#renderLevel no longer takes a DeltaTracker.
            replace("renderLevel(Lnet/minecraft/client/DeltaTracker;)V", "renderLevel()V")
            // RenderTarget#useDepth became hasDepth().
            replace(".useDepth", ".hasDepth()")
            // Sampler declarations use the generic uniform description.
            replace(".withSampler(\"InSampler\")",
                ".withUniform(\"InSampler\", UniformType.COMBINED_IMAGE_SAMPLER)")
            replace(".withSampler(\"InDepth\")",
                ".withUniform(\"InDepth\", UniformType.COMBINED_IMAGE_SAMPLER)")
        }

        string(current.parsed >= "26.1") {
            replace("classTweaker v2 named", "classTweaker v2 official")
        }
    }
}
