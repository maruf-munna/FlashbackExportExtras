/*
 * Flashback Export Extras
 * Copyright (C) RethinkQAQ
 *
 * This file is part of Flashback Export Extras.
 *
 * Flashback Export Extras is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Flashback Export Extras is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along
 * with Flashback Export Extras. If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.rethinkqaq.flashbackexportextras.gpu;

import java.util.ArrayDeque;
import java.util.Deque;

/** Owns the render-thread backend used by the current export. */
public final class GpuExportBackendFactory {
    private static GpuExportBackend backend;
    private static final Deque<GpuExportBackend> pendingRelease = new ArrayDeque<>();

    private GpuExportBackendFactory() {}

    public static synchronized GpuExportBackend get() {
        if (backend == null) backend = create();
        return backend;
    }

    public static synchronized void reset() {
        if (backend != null) pendingRelease.addLast(backend);
        backend = null;
    }

    /** The first-person hand is drawn into {@code handDepthTexture}: hand pixels of the pending depth capture become the near plane. */
    public static synchronized void captureHandDepthOnRenderThread(Object handDepthTexture) {
        if (backend != null) backend.captureHandDepth(handDepthTexture);
    }

    /** Polls completed GPU copies without creating a backend when no export used one. */
    public static synchronized void endFrameOnRenderThread() {
        if (backend != null) backend.endFrame();
    }

    /** Called only from a render-thread safe point. */
    public static synchronized void releasePendingOnRenderThread() {
        while (!pendingRelease.isEmpty()) {
            GpuExportBackend candidate = pendingRelease.peekFirst();
            if (!candidate.releaseOnRenderThread()) return;
            pendingRelease.removeFirst();
        }
    }

    private static GpuExportBackend create() {
        /*? if >=26.1 {*/
        /*return new Blaze3dExportBackend();
        *//*?} else {*/
        return new LegacyOpenGlExportBackend();
        /*?}*/
    }
}
