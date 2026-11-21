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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Shared mutable state for depth capture and camera recording.
 * Written by MixinGameRenderer, read by MixinExportJob.
 * Lives outside the mixin class to satisfy Mixin's field visibility rules.
 *
 * Also manages a small pool of FloatBuffers to avoid per-frame
 * native allocation overhead during depth readback.
 */
public class DepthCaptureState {

    /** Whether depth capture is active for the current export. */
    public static volatile boolean active = false;

    /** Render target dimensions for depth readback. */
    public static int width, height;
    public static boolean linearizeWorldSpace = true;

    /** Far plane distance used during depth capture. */
    public static volatile float depthFar = 1000.0f;

    /** FOV captured from GameRenderer.getProjectionMatrix (degrees). */
    public static volatile float fovDegrees = 70.0f;

    /**
     * Target FOV set by the keyframe interpolation system.
     * Updated by MixinFOVKeyframe whenever a FOV keyframe is evaluated.
     * This is the "desired" FOV at the current server tick, before
     * client-frame interpolation.
     */
    public static volatile float keyframeTargetFov = 70.0f;

    /** Camera position captured after renderLevel (MC world space). */
    public static volatile double camX, camY, camZ;

    /** Camera rotation captured after renderLevel (MC degrees). */
    public static volatile float camYaw, camPitch;

    private static final FrameIndexedQueue<DepthFrame> DEPTH_QUEUE = new FrameIndexedQueue<>(
            "depth", frame -> frame.frameId, frame -> releaseBuffer(frame.data));
    private static long nextExportFrameId;

    /** Frame reserved before rendering and waiting for the world-depth clear hook. */
    private static long pendingCaptureFrameId = -1L;
    private static long submittedCaptureFrameId = -1L;

    public static void beginRenderFrame() {
        IrisDepthCaptureState.beginRenderFrame();
    }

    // === Buffer pool for readback copies ===
    private static final int POOL_CAPACITY = 4;
    private static final Queue<FloatBuffer> bufferPool = new ArrayDeque<>();

    /** Acquire a FloatBuffer from the pool, or allocate a new one. */
    public static FloatBuffer acquireBuffer() {
        synchronized (bufferPool) {
            FloatBuffer buf = bufferPool.poll();
            if (buf != null) {
                buf.clear();
                return buf;
            }
        }
        return ByteBuffer.allocateDirect(width * height * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
    }

    /** Return a consumed FloatBuffer to the pool for reuse. */
    public static void releaseBuffer(FloatBuffer buf) {
        if (buf == null) return;
        synchronized (bufferPool) {
            if (bufferPool.size() < POOL_CAPACITY) {
                bufferPool.add(buf);
            }
        }
    }

    public static synchronized long nextExportFrameId() {
        return nextExportFrameId++;
    }

    /** Reserves the next frame before Flashback starts rendering it. */
    public static synchronized long reserveExportFrameId() {
        if (pendingCaptureFrameId >= 0L) {
            throw new IllegalStateException("Depth capture frame " + pendingCaptureFrameId
                    + " was not submitted before the next frame was reserved");
        }
        pendingCaptureFrameId = nextExportFrameId++;
        submittedCaptureFrameId = -1L;
        return pendingCaptureFrameId;
    }

    public static synchronized long pendingCaptureFrameId() {
        return pendingCaptureFrameId;
    }

    public static synchronized void markCaptureSubmitted(long frameId) {
        if (pendingCaptureFrameId != frameId) {
            throw new IllegalStateException("Depth capture submitted for frame " + frameId
                    + " while waiting for " + pendingCaptureFrameId);
        }
        pendingCaptureFrameId = -1L;
        submittedCaptureFrameId = frameId;
    }

    public static synchronized boolean wasCaptureSubmitted(long frameId) {
        return submittedCaptureFrameId == frameId;
    }

    public static synchronized long submittedCaptureFrameId() {
        return submittedCaptureFrameId;
    }

    public static synchronized void failPendingCapture(Throwable failure) {
        if (pendingCaptureFrameId >= 0L) {
            DEPTH_QUEUE.fail(new IllegalStateException(
                    "Depth capture was not submitted for frame " + pendingCaptureFrameId, failure));
            pendingCaptureFrameId = -1L;
            submittedCaptureFrameId = -1L;
        }
    }

    public enum Encoding {
        STANDARD_NDC,
        REVERSED_NDC,
        LINEAR_WORLD_METERS
    }

    public static final class DepthFrame {
        public final long frameId;
        public final FloatBuffer data;
        public final float zNear;
        public final float zFar;
        public final Encoding encoding;
        public final String source;

        public DepthFrame(long frameId, FloatBuffer data) {
            this(frameId, data, 0.05f, depthFar, Encoding.STANDARD_NDC, "unknown");
        }

        public DepthFrame(long frameId, FloatBuffer data, float zNear, float zFar) {
            this(frameId, data, zNear, zFar, Encoding.STANDARD_NDC, "unknown");
        }

        public DepthFrame(long frameId, FloatBuffer data, float zNear, float zFar,
                          Encoding encoding, String source) {
            this.frameId = frameId;
            this.data = data;
            this.zNear = zNear;
            this.zFar = zFar;
            this.encoding = encoding == null ? Encoding.STANDARD_NDC : encoding;
            this.source = source == null ? "unknown" : source;
        }
    }

    public static void submit(DepthFrame frame) {
        DEPTH_QUEUE.submit(frame);
    }

    public static DepthFrame peek(long expectedFrameId) {
        return DEPTH_QUEUE.peek(expectedFrameId);
    }

    public static DepthFrame poll(long expectedFrameId) {
        return DEPTH_QUEUE.poll(expectedFrameId);
    }

    public static int queuedFrameCount() {
        return DEPTH_QUEUE.size();
    }

    /** Clears all state for a new export. */
    public static void reset() {
        active = false;
        width = height = 0;
        fovDegrees = 70.0f;
        keyframeTargetFov = 70.0f;
        camX = camY = camZ = 0.0;
        camYaw = camPitch = 0.0f;
        depthFar = 1000.0f;
        nextExportFrameId = 0;
        pendingCaptureFrameId = -1L;
        submittedCaptureFrameId = -1L;
        IrisDepthCaptureState.reset();

        DEPTH_QUEUE.reset();
        synchronized (bufferPool) {
            // Buffers in this pool come from ByteBuffer.allocateDirect(), not
            // MemoryUtil.memAlloc(). Their native storage is JVM-owned and
            // must only be reclaimed by the DirectByteBuffer Cleaner.
            bufferPool.clear();
        }

    }
}
