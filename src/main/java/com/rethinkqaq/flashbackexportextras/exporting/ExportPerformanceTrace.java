/*
 * Flashback Export Extras
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.rethinkqaq.flashbackexportextras.exporting;

import com.rethinkqaq.flashbackexportextras.FlashbackExportExtras;

/** Low-frequency diagnostics for locating export pipeline stalls. */
public final class ExportPerformanceTrace {
    private static final int SAMPLE_INTERVAL = 30;

    private ExportPerformanceTrace() {}

    public static boolean sample(long frameId) {
        return frameId < 3 || frameId % SAMPLE_INTERVAL == 0;
    }

    public static void tinyExr(long frameId, long nativeWriteNanos, long fileSizeBytes,
                               int compressionType, String path) {
        if (!sample(frameId)) return;
        FlashbackExportExtras.LOGGER.debug(
                "TinyEXR write frame {}: thread={}, durationMs={}, sizeBytes={}, compression={}, path={}",
                frameId, Thread.currentThread().getName(), nanosToMillis(nativeWriteNanos),
                fileSizeBytes, compressionType, path);
    }

    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }
}
