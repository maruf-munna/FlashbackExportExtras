package com.rethinkqaq.flashbackexportextras.exporting;

import com.rethinkqaq.flashbackexportextras.FlashbackExportExtras;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Method;

/** Optional bridge to FlashPlus scene HUD/cursor tracks without a compile-time mod dependency. */
public final class FlashPlusUiBridge {
    private static final String EXPORT_CLASS = "redsmods.flashplus.scene.ScenePerspectiveExport";
    private static Method separateUiMethod;
    private static Method blendUiMethod;
    private static Method split3dUiMethod;
    private static boolean lookedUp;
    private static boolean warned;

    private FlashPlusUiBridge() { }

    public static synchronized void setSeparateUiOutput(boolean enabled) {
        lookup();
        if (separateUiMethod == null) return;
        try {
            separateUiMethod.invoke(null, enabled);
        } catch (ReflectiveOperationException e) {
            warn(e);
        }
    }

    /** Bit 1 requests a separate HUD and bit 2 requests a separate cursor. */
    public static synchronized int set3dUiRouting(boolean enabled) {
        lookup();
        if (split3dUiMethod == null) return 0;
        try {
            return (Integer) split3dUiMethod.invoke(null, enabled);
        } catch (ReflectiveOperationException e) {
            warn(e);
            return 0;
        }
    }

    static synchronized void blend(java.nio.ByteBuffer frame, int width, int height) {
        lookup();
        if (blendUiMethod == null || frame == null) return;
        try {
            blendUiMethod.invoke(null, MemoryUtil.memAddress(frame), width, height);
        } catch (ReflectiveOperationException e) {
            warn(e);
        }
    }

    private static void lookup() {
        if (lookedUp) return;
        lookedUp = true;
        try {
            Class<?> owner = Class.forName(EXPORT_CLASS);
            separateUiMethod = owner.getMethod("setSeparateUiOutput", boolean.class);
            blendUiMethod = owner.getMethod("blendNextExrUiFrame", long.class, int.class, int.class);
            split3dUiMethod = owner.getMethod("set3dUiRouting", boolean.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // FlashPlus is optional; ordinary EXR HUD layers remain independent.
        }
    }

    private static void warn(Exception e) {
        if (warned) return;
        warned = true;
        FlashbackExportExtras.LOGGER.warn("FlashPlus scene overlay bridge failed: {}", e.toString());
    }
}
