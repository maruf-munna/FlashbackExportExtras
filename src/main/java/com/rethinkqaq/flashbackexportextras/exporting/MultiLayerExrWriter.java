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

import com.mojang.blaze3d.platform.NativeImage;
import com.rethinkqaq.flashbackexportextras.FlashbackExportExtras;
import com.rethinkqaq.flashbackexportextras.FlashbackExportExtrasConfig.ExrCompression;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.tinyexr.EXRChannelInfo;
import org.lwjgl.util.tinyexr.EXRAttribute;
import org.lwjgl.util.tinyexr.EXRHeader;
import org.lwjgl.util.tinyexr.EXRImage;
import org.lwjgl.util.tinyexr.TinyEXR;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Multi-layer OpenEXR writer using LWJGL tinyexr bindings.
 * Same approach as ReplayMod — SaveEXRImageToFile with EXRHeader/EXRImage.
 *
 * Perf: All native buffers and EXR structs are pre-allocated once and reused
 * across frames to eliminate per-frame allocation overhead (~40MB/frame).
 */
public class MultiLayerExrWriter implements AutoCloseable {

    private static final float INV_255 = 1.0f / 255.0f;
    // header strings: camera of the frame (Minecraft world space, degrees), shown in Blender's Metadata panel
    private static final String[] CAMERA_ATTRIBUTES = {
        "mc.frame", "mc.camera.position", "mc.camera.rotation", "mc.camera.fov", "mc.channels"
    };

    // ReplayMod-proven order: A, B, G, R (matches BGRA pixel data layout); alphabetical as OpenEXR wants it.
    // Export Transparent adds the FlashPlus live-take layers: Trans (premultiplied RGBA) and TransDepth.
    private static final String[] CHANNEL_NAMES = {
        "View Layer.Combined.A",
        "View Layer.Combined.B",
        "View Layer.Combined.G",
        "View Layer.Combined.R",
        "View Layer.Depth.Z",
        "View Layer.Trans.A",
        "View Layer.Trans.B",
        "View Layer.Trans.G",
        "View Layer.Trans.R",
        "View Layer.TransDepth.Z"
    };

    private final int numChannels;
    private final boolean translucentLayer;

    private final Path outputDir;
    private final int width;
    private final int height;
    private final boolean linearizeDepth;
    private final boolean sceneLinearHdr;
    private final int compressionType;
    private int frameCount;
    private boolean closed = false;

    // === Pre-allocated pixel buffers (reused every frame) ===
    private final FloatBuffer rBuf, gBuf, bBuf, aBuf, zBuf;
    // translucent layer (null when Export Transparent is off)
    private final FloatBuffer trBuf, tgBuf, tbBuf, taBuf, tzBuf;

    // === Pre-allocated EXR data structures ===
    private final EXRHeader header;
    private final EXRImage image;
    private final EXRChannelInfo.Buffer channelInfo;
    private final IntBuffer pixelTypes;
    private final IntBuffer requestedTypes;
    private final PointerBuffer imagePtrs;
    private final List<ByteBuffer> nameBufs;
    private final EXRAttribute.Buffer customAttributes;
    private final List<ByteBuffer> customAttributeBuffers;
    // fixed attributes (chromaticities) first, then the per-frame camera strings
    private final int fixedAttributeCount;
    private final List<ByteBuffer> frameAttributeBuffers = new ArrayList<>();
    // this frame has the translucent split (else glass is in Combined / Depth and Trans is empty)
    private boolean frameSplit;

    public MultiLayerExrWriter(Path outputDir, int width, int height, boolean linearizeDepth,
                               boolean sceneLinearHdr, ExrCompression compression) throws IOException {
        this(outputDir, width, height, linearizeDepth, sceneLinearHdr, compression, false);
    }

    public MultiLayerExrWriter(Path outputDir, int width, int height, boolean linearizeDepth,
                               boolean sceneLinearHdr, ExrCompression compression,
                               boolean translucentLayer) throws IOException {
        this.translucentLayer = translucentLayer;
        this.numChannels = translucentLayer ? 10 : 5;
        final int NUM_CHANNELS = numChannels;
        this.outputDir = outputDir;
        this.width = width;
        this.height = height;
        this.linearizeDepth = linearizeDepth;
        this.sceneLinearHdr = sceneLinearHdr;
        this.compressionType = switch (compression == null ? ExrCompression.ZIP : compression) {
            case NONE -> TinyEXR.TINYEXR_COMPRESSIONTYPE_NONE;
            case ZIPS -> TinyEXR.TINYEXR_COMPRESSIONTYPE_ZIPS;
            case ZIP -> TinyEXR.TINYEXR_COMPRESSIONTYPE_ZIP;
        };
        this.frameCount = 0;
        Files.createDirectories(outputDir);

        int pixelCount = width * height;

        // --- Pre-allocate pixel buffers (5 × width × height × 4 bytes) ---
        this.rBuf = MemoryUtil.memAllocFloat(pixelCount);
        this.gBuf = MemoryUtil.memAllocFloat(pixelCount);
        this.bBuf = MemoryUtil.memAllocFloat(pixelCount);
        this.aBuf = MemoryUtil.memAllocFloat(pixelCount);
        this.zBuf = MemoryUtil.memAllocFloat(pixelCount);
        this.trBuf = translucentLayer ? MemoryUtil.memAllocFloat(pixelCount) : null;
        this.tgBuf = translucentLayer ? MemoryUtil.memAllocFloat(pixelCount) : null;
        this.tbBuf = translucentLayer ? MemoryUtil.memAllocFloat(pixelCount) : null;
        this.taBuf = translucentLayer ? MemoryUtil.memAllocFloat(pixelCount) : null;
        this.tzBuf = translucentLayer ? MemoryUtil.memAllocFloat(pixelCount) : null;

        // --- Pre-allocate EXR data structures ---
        this.header = EXRHeader.calloc();
        this.image = EXRImage.calloc();
        this.channelInfo = EXRChannelInfo.calloc(NUM_CHANNELS);
        this.pixelTypes = MemoryUtil.memAllocInt(NUM_CHANNELS);
        this.requestedTypes = MemoryUtil.memAllocInt(NUM_CHANNELS);
        this.imagePtrs = MemoryUtil.memAllocPointer(NUM_CHANNELS);
        this.nameBufs = new ArrayList<>(NUM_CHANNELS);
        this.customAttributeBuffers = new ArrayList<>();

        // --- Pre-configure immutable channel metadata ---
        for (int i = 0; i < NUM_CHANNELS; i++) {
            ByteBuffer nameBuf = MemoryUtil.memUTF8(CHANNEL_NAMES[i]);
            nameBufs.add(nameBuf);
            channelInfo.get(i).name(nameBuf);
            pixelTypes.put(i, TinyEXR.TINYEXR_PIXELTYPE_FLOAT);
            // RGBA → HALF output; Depth → FLOAT output (ReplayMod pattern)
            boolean depthChannel = CHANNEL_NAMES[i].endsWith(".Z");
            requestedTypes.put(i, depthChannel ? TinyEXR.TINYEXR_PIXELTYPE_FLOAT : TinyEXR.TINYEXR_PIXELTYPE_HALF);
        }
        pixelTypes.flip();
        requestedTypes.flip();

        // --- Pre-set image pointer table (native addresses are stable) ---
        // Channel order: A, B, G, R, Z
        imagePtrs.put(0, MemoryUtil.memAddress(aBuf));
        imagePtrs.put(1, MemoryUtil.memAddress(bBuf));
        imagePtrs.put(2, MemoryUtil.memAddress(gBuf));
        imagePtrs.put(3, MemoryUtil.memAddress(rBuf));
        imagePtrs.put(4, MemoryUtil.memAddress(zBuf));
        if (translucentLayer) {
            imagePtrs.put(5, MemoryUtil.memAddress(taBuf));
            imagePtrs.put(6, MemoryUtil.memAddress(tbBuf));
            imagePtrs.put(7, MemoryUtil.memAddress(tgBuf));
            imagePtrs.put(8, MemoryUtil.memAddress(trBuf));
            imagePtrs.put(9, MemoryUtil.memAddress(tzBuf));
        }
        imagePtrs.flip();

        fixedAttributeCount = sceneLinearHdr ? 1 : 0;
        customAttributes = EXRAttribute.calloc(fixedAttributeCount + CAMERA_ATTRIBUTES.length);
        if (sceneLinearHdr) {
            ByteBuffer attributeName = MemoryUtil.memUTF8("chromaticities");
            ByteBuffer attributeType = MemoryUtil.memUTF8("chromaticities");
            ByteBuffer chromaticities = MemoryUtil.memAlloc(8 * Float.BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN);
            // Rec.709/sRGB primaries and D65 white, in OpenEXR chromaticities order.
            chromaticities.putFloat(0.6400f).putFloat(0.3300f);
            chromaticities.putFloat(0.3000f).putFloat(0.6000f);
            chromaticities.putFloat(0.1500f).putFloat(0.0600f);
            chromaticities.putFloat(0.3127f).putFloat(0.3290f).flip();
            customAttributes.get(0)
                    .name(attributeName)
                    .type(attributeType)
                    .value(chromaticities);
            customAttributeBuffers.add(attributeName);
            customAttributeBuffers.add(attributeType);
            customAttributeBuffers.add(chromaticities);
        }
    }

    /**
     * Writes one multi-layer EXR frame.
     * Fills pre-allocated buffers with new data, then calls tinyexr.
     */
    public void writeFrame(NativeImage colorImage, DepthCaptureState.DepthFrame depthFrame,
                           int frameNumber) throws IOException {
        writeFrame(colorImage, depthFrame, null, null, frameNumber);
    }

    /**
     * Writes one frame; with Export Transparent and a split frame, Combined = the solid scene (no glass), Depth = its
     * depth (already solid, see the depth capture) and Trans / TransDepth = the translucent layer, like a FlashPlus
     * live take. {@code camera} (may be null) goes into the header.
     */
    public void writeFrame(NativeImage colorImage, DepthCaptureState.DepthFrame depthFrame,
                           TranslucentCaptureState.TransFrame trans, TranslucentCaptureState.CameraInfo camera,
                           int frameNumber) throws IOException {
        frameSplit = translucentLayer && trans != null && trans.hasSplit();
        if (frameSplit) {
            fillRgba8BottomUp(trans.solid, rBuf, gBuf, bBuf, aBuf, true);
            fillRgba8BottomUp(trans.layer, trBuf, tgBuf, tbBuf, taBuf, false);
            fillTransDepth(trans);
        } else {
            fillSdrColor(colorImage);
            if (translucentLayer) {
                clearTranslucent(depthFrame);
            }
        }
        fillDepth(depthFrame);
        writeExr(frameNumber, camera);
        frameCount++;
    }

    /** RGBA8 rows bottom-up -> top-down float channels (0..1, the sRGB-encoded values Combined has). */
    private void fillRgba8BottomUp(ByteBuffer rgba, FloatBuffer r, FloatBuffer g, FloatBuffer b, FloatBuffer a,
                                   boolean opaque) {
        float inv255 = INV_255;
        for (int y = 0; y < height; y++) {
            int dst = y * width;
            int src = (height - 1 - y) * width * 4;
            for (int x = 0; x < width; x++, dst++, src += 4) {
                r.put(dst, (rgba.get(src) & 0xFF) * inv255);
                g.put(dst, (rgba.get(src + 1) & 0xFF) * inv255);
                b.put(dst, (rgba.get(src + 2) & 0xFF) * inv255);
                a.put(dst, opaque ? 1.0f : (rgba.get(src + 3) & 0xFF) * inv255);
            }
        }
    }

    /** Translucent depth (reverse-Z, 0 = nothing) -> the same encoding as Depth (metres when linearized). */
    private void fillTransDepth(TranslucentCaptureState.TransFrame trans) {
        FloatBuffer raw = trans.layerDepth.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (int y = 0; y < height; y++) {
            int dst = y * width;
            int src = (height - 1 - y) * width;
            for (int x = 0; x < width; x++, dst++, src++) {
                tzBuf.put(dst, encodeReversedDepth(raw.get(src), trans.zNear, trans.zFar));
            }
        }
    }

    /** Frame without a split (Improved Transparency off): an empty layer at the far plane. */
    private void clearTranslucent(DepthCaptureState.DepthFrame depthFrame) {
        float far = encodeReversedDepth(0.0f, 0.05f, depthFrame == null ? 1000.0f : depthFrame.zFar);
        int pixelCount = width * height;
        for (int i = 0; i < pixelCount; i++) {
            trBuf.put(i, 0.0f);
            tgBuf.put(i, 0.0f);
            tbBuf.put(i, 0.0f);
            taBuf.put(i, 0.0f);
            tzBuf.put(i, far);
        }
    }

    /** Same transform as the depth copy shader: reverse-Z -> standard NDC, then metres when linearized. */
    private float encodeReversedDepth(float depth, float znear, float zfar) {
        depth = 1.0f - depth;
        if (!linearizeDepth) {
            return depth;
        }
        return 2.0f * znear * zfar / (zfar + znear - (2.0f * depth - 1.0f) * (zfar - znear));
    }

    /** Writes scene-linear Rec.709 RGBA16F color with the matching depth frame. */
    public void writeHdrFrame(ByteBuffer rgba16f, DepthCaptureState.DepthFrame depthFrame,
                              int frameNumber) throws IOException {
        fillSceneLinearHdrColor(rgba16f);
        fillDepth(depthFrame);
        frameSplit = false;
        writeExr(frameNumber, null);
        frameCount++;
    }

    /**
     * Fills the pre-allocated float buffers from NativeImage and depth buffer.
     *
     * Uses getPixelsRGBA() for a single bulk copy (one memcpy) instead of
     * width*height individual JNI calls to getPixelRGBA(x,y).
     */
    private void fillSdrColor(NativeImage colorImage) {
        float inv255 = INV_255;
        int pixelCount = width * height;

        // --- Pass 1: Bulk copy pixels, then extract RGBA in pure Java ---
        /*? if >=1.21.4 {*/
        /*int[] pixelArray = colorImage.getPixelsABGR();
        *//*?} else {*/
        int[] pixelArray = colorImage.getPixelsRGBA();
        /*?}*/
        for (int i = 0; i < pixelCount; i++) {
            int pixel = pixelArray[i];
            // 0xAABBGGRR → individual float channels
            rBuf.put(i, (pixel & 0xFF) * inv255);
            gBuf.put(i, ((pixel >> 8) & 0xFF) * inv255);
            bBuf.put(i, ((pixel >> 16) & 0xFF) * inv255);
            // Force full opacity: Iris shaders may write non-0xFF alpha for compositing/HDR
            aBuf.put(i, 1.0f);
        }
    }

    private void fillSceneLinearHdrColor(ByteBuffer rgba16f) {
        int pixelCount = width * height;
        int requiredBytes = pixelCount * 8;
        ByteBuffer data = rgba16f.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        data.rewind();
        if (data.remaining() < requiredBytes) {
            throw new IllegalArgumentException("RGBA16F frame is too small: "
                    + data.remaining() + " < " + requiredBytes);
        }
        for (int i = 0; i < pixelCount; i++) {
            int offset = i * 8;
            rBuf.put(i, halfToFloat(data.getShort(offset)));
            gBuf.put(i, halfToFloat(data.getShort(offset + 2)));
            bBuf.put(i, halfToFloat(data.getShort(offset + 4)));
            aBuf.put(i, halfToFloat(data.getShort(offset + 6)));
        }
    }

    private static float halfToFloat(short half) {
        int bits = half & 0xffff;
        int sign = (bits & 0x8000) << 16;
        int exponent = (bits >>> 10) & 0x1f;
        int mantissa = bits & 0x03ff;
        if (exponent == 0) {
            if (mantissa == 0) return Float.intBitsToFloat(sign);
            while ((mantissa & 0x0400) == 0) {
                mantissa <<= 1;
                exponent--;
            }
            exponent++;
            mantissa &= ~0x0400;
        } else if (exponent == 31) {
            return Float.intBitsToFloat(sign | 0x7f800000 | (mantissa << 13));
        }
        int floatExponent = exponent + (127 - 15);
        return Float.intBitsToFloat(sign | (floatExponent << 23) | (mantissa << 13));
    }

    private void fillDepth(DepthCaptureState.DepthFrame depthFrame) {
        if (depthFrame == null || depthFrame.data == null) {
            throw new IllegalArgumentException("Missing depth frame");
        }
        FloatBuffer depthBuffer = depthFrame.data;

        // --- Pass 2: Fill depth (Y-flipped from GL bottom-up) ---
        if (linearizeDepth && depthFrame.encoding != DepthCaptureState.Encoding.LINEAR_WORLD_METERS) {
            float znear = depthFrame.zNear;
            float zfar = depthFrame.zFar;
            float twoZnZf = 2.0f * znear * zfar;
            float zfMinusZn = zfar - znear;
            float zfPlusZn = zfar + znear;

            for (int y = 0; y < height; y++) {
                int dstIdx = y * width;
                int srcY = height - 1 - y;
                for (int x = 0; x < width; x++, dstIdx++) {
                    float depth = depthBuffer.get(srcY * width + x);
                    if (depthFrame.encoding == DepthCaptureState.Encoding.REVERSED_NDC) {
                        depth = 1.0f - depth;
                    }
                    depth = twoZnZf / (zfPlusZn - (2.0f * depth - 1.0f) * zfMinusZn);
                    zBuf.put(dstIdx, depth);
                }
            }
        } else {
            for (int y = 0; y < height; y++) {
                int dstIdx = y * width;
                int srcY = height - 1 - y;
                for (int x = 0; x < width; x++, dstIdx++) {
                    float depth = depthBuffer.get(srcY * width + x);
                    if (depthFrame.encoding == DepthCaptureState.Encoding.REVERSED_NDC) {
                        depth = 1.0f - depth;
                    }
                    zBuf.put(dstIdx, depth);
                }
            }
        }

    }

    /**
     * Writes the current buffer contents to an EXR file.
     * Reconfigures header/image each call (Init zeros fields; we re-apply).
     */
    private void writeExr(int frameNumber, TranslucentCaptureState.CameraInfo camera) throws IOException {
        Path filePath = outputDir.resolve(String.format("%04d.exr", frameNumber));

        // Reset header/image to zero (InitEXR does memset), then re-apply config
        TinyEXR.InitEXRHeader(header);
        TinyEXR.InitEXRImage(image);

        header.channels(channelInfo);
        header.num_channels(numChannels);
        header.pixel_types(pixelTypes);
        header.requested_pixel_types(requestedTypes);
        int attributes = fixedAttributeCount + setFrameAttributes(frameNumber, camera);
        if (attributes > 0) {
            header.num_custom_attributes(attributes);
            header.custom_attributes(customAttributes);
        }
        header.compression_type(compressionType);

        image.width(width);
        image.height(height);
        image.num_channels(numChannels);
        imagePtrs.rewind();
        image.images(imagePtrs);

        ByteBuffer pathBuf = MemoryUtil.memUTF8(filePath.toAbsolutePath().toString());
        PointerBuffer err = MemoryUtil.memAllocPointer(1);
        try {
            long tinyExrStarted = System.nanoTime();
            int result = TinyEXR.SaveEXRImageToFile(image, header, pathBuf, err);
            long tinyExrNanos = System.nanoTime() - tinyExrStarted;
            if (result != 0) {
                long errAddr = err.get(0);
                String error = errAddr != 0 ? MemoryUtil.memUTF8(errAddr) : "unknown error";
                throw new IOException("tinyexr SaveEXRImageToFile failed: " + error + " (code " + result + ")");
            }
            long fileSize = Files.exists(filePath) ? Files.size(filePath) : -1L;
            ExportPerformanceTrace.tinyExr(frameNumber, tinyExrNanos, fileSize,
                    compressionType, filePath.toString());
        } finally {
            MemoryUtil.memFree(err);
            MemoryUtil.memFree(pathBuf);
            freeFrameAttributes();
        }
    }

    /** Header strings of this frame (after the fixed attributes); returns how many were set. */
    private int setFrameAttributes(int frameNumber, TranslucentCaptureState.CameraInfo camera) {
        String layers = !translucentLayer ? "Combined, Depth"
                : frameSplit ? "Combined (solid scene), Depth (solid), Trans (premultiplied), TransDepth"
                : "Combined (glass baked in: no translucent layer this frame), Depth, Trans (empty), TransDepth";
        String[] values = camera == null
                ? new String[] {Integer.toString(frameNumber), null, null, null, layers}
                : new String[] {
                        Integer.toString(frameNumber),
                        String.format(java.util.Locale.ROOT, "%.6f %.6f %.6f", camera.x(), camera.y(), camera.z()),
                        String.format(java.util.Locale.ROOT, "%.4f %.4f", camera.yaw(), camera.pitch()),
                        String.format(java.util.Locale.ROOT, "%.4f", camera.fov()),
                        layers};
        int count = 0;
        for (int i = 0; i < CAMERA_ATTRIBUTES.length; i++) {
            if (values[i] == null) {
                continue;
            }
            ByteBuffer name = MemoryUtil.memUTF8(CAMERA_ATTRIBUTES[i]);
            ByteBuffer type = MemoryUtil.memUTF8("string");
            ByteBuffer value = MemoryUtil.memUTF8(values[i], false);
            frameAttributeBuffers.add(name);
            frameAttributeBuffers.add(type);
            frameAttributeBuffers.add(value);
            customAttributes.get(fixedAttributeCount + count).name(name).type(type).value(value);
            count++;
        }
        return count;
    }

    private void freeFrameAttributes() {
        for (ByteBuffer buffer : frameAttributeBuffers) {
            MemoryUtil.memFree(buffer);
        }
        frameAttributeBuffers.clear();
    }

    public int getFrameCount() { return frameCount; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;

        // Free all pre-allocated native memory
        MemoryUtil.memFree(rBuf);
        MemoryUtil.memFree(gBuf);
        MemoryUtil.memFree(bBuf);
        MemoryUtil.memFree(aBuf);
        MemoryUtil.memFree(zBuf);
        for (FloatBuffer b : new FloatBuffer[] {trBuf, tgBuf, tbBuf, taBuf, tzBuf}) {
            if (b != null) MemoryUtil.memFree(b);
        }
        freeFrameAttributes();
        MemoryUtil.memFree(imagePtrs);
        MemoryUtil.memFree(pixelTypes);
        MemoryUtil.memFree(requestedTypes);
        for (ByteBuffer b : nameBufs) MemoryUtil.memFree(b);
        for (ByteBuffer b : customAttributeBuffers) MemoryUtil.memFree(b);
        customAttributes.free();
        channelInfo.free();
        image.free();
        header.free();
    }
}
