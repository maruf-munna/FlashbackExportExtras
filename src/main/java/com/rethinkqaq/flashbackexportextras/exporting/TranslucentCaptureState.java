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
package com.rethinkqaq.flashbackexportextras.exporting;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Export Transparent: per EXR frame the solid scene colour and the translucent layer (glass, water, particles,
 * weather, clouds as premultiplied RGBA + its depth), like a FlashPlus live take. Filled by the GPU side
 * (TranslucentExportCapture, 26.3), read by the EXR writer. Also holds the camera of every frame for the EXR header.
 */
public final class TranslucentCaptureState {

    /** This export writes Combined = solid scene, Trans and TransDepth (EXR mode + toggle + supported version). */
    public static volatile boolean active = false;

    /** One frame of the split; buffers are bottom-up (GL rows) and owned by the frame until released. */
    public static final class TransFrame {
        public final long frameId;
        /** Solid scene colour (RGBA8), null = no split this frame (keep Flashback's colour). */
        public final ByteBuffer solid;
        /** Translucent layer, premultiplied RGBA8 (sRGB-encoded like the scene), null = none. */
        public final ByteBuffer layer;
        /** Nearest translucent depth as stored by the GPU (reverse-Z, 0 = nothing), null = none. */
        public final ByteBuffer layerDepth;
        public final float zNear;
        public final float zFar;

        public TransFrame(long frameId, ByteBuffer solid, ByteBuffer layer, ByteBuffer layerDepth,
                          float zNear, float zFar) {
            this.frameId = frameId;
            this.solid = solid;
            this.layer = layer;
            this.layerDepth = layerDepth;
            this.zNear = zNear;
            this.zFar = zFar;
        }

        public boolean hasSplit() {
            return solid != null && layer != null && layerDepth != null;
        }

        public void release() {
            if (solid != null) MemoryUtil.memFree(solid);
            if (layer != null) MemoryUtil.memFree(layer);
            if (layerDepth != null) MemoryUtil.memFree(layerDepth);
        }
    }

    /** Camera the frame was rendered with (Minecraft world space, degrees), written into the EXR header. */
    public record CameraInfo(double x, double y, double z, float yaw, float pitch, float fov) {
    }

    private static final FrameIndexedQueue<TransFrame> QUEUE = new FrameIndexedQueue<>(
            "translucent layer", frame -> frame.frameId, TransFrame::release);
    private static final Map<Long, CameraInfo> CAMERAS = new ConcurrentHashMap<>();

    private TranslucentCaptureState() {
    }

    public static void submit(TransFrame frame) {
        QUEUE.submit(frame);
    }

    public static TransFrame peek(long frameId) {
        return QUEUE.peek(frameId);
    }

    public static TransFrame poll(long frameId) {
        return QUEUE.poll(frameId);
    }

    public static int size() {
        return QUEUE.size();
    }

    public static void fail(Throwable failure) {
        QUEUE.fail(failure);
    }

    public static void putCamera(long frameId, CameraInfo camera) {
        CAMERAS.put(frameId, camera);
    }

    public static CameraInfo takeCamera(long frameId) {
        return CAMERAS.remove(frameId);
    }

    public static void reset() {
        active = false;
        QUEUE.reset();
        CAMERAS.clear();
    }
}
