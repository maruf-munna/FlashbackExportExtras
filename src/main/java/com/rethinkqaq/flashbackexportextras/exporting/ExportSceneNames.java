package com.rethinkqaq.flashbackexportextras.exporting;

/** Optional FlashPlus scene identity, used for EXR sequence folders. */
public final class ExportSceneNames {
    private ExportSceneNames() {}
    public static String baseName(String fallback) {
        try {
            Object value = Class.forName("redsmods.flashplus.scene.ReplaySceneBinding")
                    .getMethod("currentExportBaseName").invoke(null);
            if (value instanceof String name && !name.isBlank()) return name;
        } catch (ReflectiveOperationException | LinkageError ignored) { }
        return fallback;
    }
    public static java.nio.file.Path availableFolder(java.nio.file.Path root, String name) {
        java.nio.file.Path target = root.resolve(name);
        for (int suffix = 2; java.nio.file.Files.exists(target); suffix++) target = root.resolve(name + "-" + suffix);
        return target;
    }
}
