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
package com.rethinkqaq.flashbackexportextras.mixins;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.moulberry.flashback.combo_options.VideoContainer;
import com.moulberry.flashback.exporting.*;
/*? if >=26.1 {*/
/*import com.moulberry.flashback.exporting.ImageFrame;
*//*?}*/
import com.rethinkqaq.flashbackexportextras.FlashbackExportExtrasConfig;
import com.rethinkqaq.flashbackexportextras.FlashbackExportExtrasConfig.ExportMode;
import com.rethinkqaq.flashbackexportextras.FlashbackExportExtras;
import com.rethinkqaq.flashbackexportextras.exporting.*;
import com.rethinkqaq.flashbackexportextras.gpu.GpuExportBackendFactory;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
/*? if >=26.1 {*/
/*import org.spongepowered.asm.mixin.injection.Coerce;
*//*?}*/
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Mixin(value = ExportJob.class, remap = false)
public class MixinExportJob {

    private static final DateTimeFormatter EXR_DEFAULT_NAME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'_HH_mm");

    @Shadow
    private ExportSettings settings;

    @Shadow
    private void doExport(VideoWriter videoWriter, SaveableFramebufferQueue downloader) {
        throw new AssertionError("Mixin shadow");
    }

    @Unique
    private CameraPathExporter cameraExporter;

    @Unique
    private boolean isExrMode;

    @Unique
    private boolean isExrSceneLinearHdr;

    @Unique
    private boolean isHdrMode;

    // Export Transparent for this export (EXR, SDR colour, 26.3)
    @Unique
    private boolean isExrTranslucentLayer;

    @Unique
    private int flashbackexportextras_originalDummyFrames;

    @Unique
    private boolean flashbackexportextras_dummyFramesOverridden;

    @Unique
    private boolean flashbackexportextras_sessionActive;

    /**
     * The next EXR color download must consume the depth frame reserved for
     * the same Flashback output frame.  This counter is local to the export
     * session and deliberately independent from the asynchronous queue size.
     */
    @Unique
    private long flashbackexportextras_nextDepthColorFrameId;

    /*? if hdr {*/
    @Unique
    private HdrVideoWriter hdrWriterRef;

    @Unique
    private long flashbackexportextras_hdrCaptureFrameCount;
    /*?}*/

    // === Redirect createVideoWriter ===

    /*? if >=26.1 {*/
    /*@Redirect(method = "run",
            at = @At(value = "INVOKE",
                    target = "Lcom/moulberry/flashback/exporting/ExportJob;createVideoWriter(Lcom/moulberry/flashback/exporting/ExportSettings;Lcom/moulberry/flashback/exporting/ExportJob$TempFileInfo;)Lcom/moulberry/flashback/exporting/VideoWriter;"),
            remap = false)
    private VideoWriter redirectCreateWriter(ExportSettings settings, @Coerce Object tempFileInfo) throws IOException {
        String tempFileName = tempFileInfo == null ? null
                : ((ExportJobTempFileInfoAccess) tempFileInfo).flashbackexportextras$getName();
        return flashbackexportextras$createWriter(settings, tempFileName);
    }
    *//*?} else {*/
    @Redirect(method = "run",
            at = @At(value = "INVOKE",
                    target = "Lcom/moulberry/flashback/exporting/ExportJob;createVideoWriter(Lcom/moulberry/flashback/exporting/ExportSettings;Ljava/lang/String;)Lcom/moulberry/flashback/exporting/VideoWriter;"),
            remap = false)
    private VideoWriter redirectCreateWriter(ExportSettings settings, String tempFileName) throws IOException {
        return flashbackexportextras$createWriter(settings, tempFileName);
    }
    /*?}*/

    @Unique
    private VideoWriter flashbackexportextras$createWriter(ExportSettings settings, String tempFileName) throws IOException {
        FlashbackExportExtras.LOGGER.info(
                "ExportJob creating writer: output={}, container={}, resolution={}x{}, temp={}",
                settings.output(), settings.container(), settings.resolutionX(), settings.resolutionY(), tempFileName);
        flashbackexportextras$configureExportModes();
        if (isExrMode && FlashbackExportExtrasConfig.INSTANCE.exrSceneLinearHdr && !isExrSceneLinearHdr) {
            FlashbackExportExtras.LOGGER.warn(
                    "Scene-linear HDR EXR is unavailable in this runtime; exporting standard SDR color");
        }
        if (isExrMode) {
            String configuredName = FlashbackExportExtrasConfig.INSTANCE.exrOutputName == null
                    ? "" : FlashbackExportExtrasConfig.INSTANCE.exrOutputName.trim();
            String outputName = configuredName.isEmpty()
                    ? LocalDateTime.now().format(EXR_DEFAULT_NAME_FORMAT)
                    : configuredName;
            outputName = outputName.replaceAll("[^A-Za-z0-9._-]", "_");
            if (outputName.isEmpty() || outputName.equals(".") || outputName.equals("..")) {
                outputName = "export";
            }
            Path outputRoot = settings.output().toAbsolutePath().normalize();
            Path outputDir = outputRoot.resolve(outputName).normalize();
            if (!outputDir.getParent().equals(outputRoot)) {
                throw new IOException("Invalid EXR output name: " + configuredName);
            }
            FlashbackExportExtras.LOGGER.info("OpenEXR frame output directory: {}", outputDir);
            int w = settings.resolutionX();
            int h = settings.resolutionY();
            return new ExrVideoWriter(outputDir, w, h, isExrSceneLinearHdr,
                    FlashbackExportExtrasConfig.INSTANCE.getExrCompression(), isExrTranslucentLayer);
        }
        /*? if hdr {*/
        if (isHdrMode) {
            Path tempPath = java.nio.file.Path.of(tempFileName);
            int w = settings.resolutionX();
            int h = settings.resolutionY();
            FlashbackExportExtras.LOGGER.info("HDR export temporary path: {}, final path: {}", tempPath, settings.output());
            int bitrate = settings.bitrate() > 0
                    ? settings.bitrate()
                    : Math.min(288_000_000,
                            5_000 + (int) Math.ceil(w * (double) h * settings.framerate()));
            hdrWriterRef = new HdrVideoWriter(tempPath, w, h, settings.framerate(), bitrate);
            return hdrWriterRef;
        }
        /*?}*/
        if (settings.container() == VideoContainer.PNG_SEQUENCE) {
            return new PNGSequenceVideoWriter(settings);
        } else {
            return new AsyncFFmpegVideoWriter(settings, tempFileName);
        }
    }

    @Unique
    private void flashbackexportextras$configureExportModes() {
        /*? if hdr {*/
        isHdrMode = FlashbackExportExtrasConfig.INSTANCE.getExportMode() == ExportMode.HDR10
                && HdrExportState.isAvailable() && GpuExportBackendFactory.get().supportsHdr();
        /*?} else {*/
        /*isHdrMode = false;
        *//*?}*/
        isExrMode = FlashbackExportExtrasConfig.INSTANCE.getExportMode() == ExportMode.EXR;
        isExrSceneLinearHdr = isExrMode && FlashbackExportExtrasConfig.INSTANCE.exrSceneLinearHdr
                && HdrExportState.isAvailable()
                && GpuExportBackendFactory.get().supportsSceneLinearHdr();
        // the layer is 8-bit sRGB like the scene: not with scene-linear HDR colour
        isExrTranslucentLayer = isExrMode && !isExrSceneLinearHdr
                && FlashbackExportExtrasConfig.INSTANCE.exrTranslucentLayer && FlashbackExportExtras.TRANSLUCENT_LAYER_SUPPORTED;
        if (isExrMode && FlashbackExportExtrasConfig.INSTANCE.exrTranslucentLayer && !isExrTranslucentLayer) {
            FlashbackExportExtras.LOGGER.warn("Export Transparent is off for this export ({})",
                    isExrSceneLinearHdr ? "scene-linear HDR colour" : "needs Minecraft 26.3");
        }
    }

    // === doExport HEAD ===

    @Inject(method = "doExport", at = @At("HEAD"), remap = false)
    private void onDoExportStart(VideoWriter videoWriter, SaveableFramebufferQueue downloader,
                                  CallbackInfo ci) {
        FlashbackExportExtras.LOGGER.info("ExportJob doExport started: writer={}",
                videoWriter == null ? "null" : videoWriter.getClass().getName());
        flashbackexportextras_sessionActive = true;
        flashbackexportextras_nextDepthColorFrameId = 0L;
        if (isExrMode) {
            com.moulberry.flashback.configuration.FlashbackConfigV1 config =
                    com.moulberry.flashback.Flashback.getConfig();
            flashbackexportextras_originalDummyFrames = config.exporting.exportRenderDummyFrames;
            config.exporting.exportRenderDummyFrames = 0;
            flashbackexportextras_dummyFramesOverridden = true;
            FlashbackExportExtras.LOGGER.info("EXR export: disabled {} Flashback warm-up frame(s)",
                    flashbackexportextras_originalDummyFrames);
        }
        DepthCaptureState.reset();
        SceneLinearHdrCaptureState.reset();
        TranslucentCaptureState.reset();
        //? if >=26.3 {
        /*com.rethinkqaq.flashbackexportextras.gpu.TranslucentExportCapture.INSTANCE.reset();
        *///?}
        TranslucentCaptureState.active = isExrTranslucentLayer;
        /*? if hdr {*/
        HdrVideoCaptureState.reset();
        flashbackexportextras_hdrCaptureFrameCount = 0L;
        /*?}*/

        ExportJob self = (ExportJob) (Object) this;

        if (isExrMode) {
            DepthCaptureState.width = self.getWidth();
            DepthCaptureState.height = self.getHeight();
            DepthCaptureState.active = true;
        }

        /*? if hdr {*/
        if (isHdrMode) {
            int w = self.getWidth();
            int h = self.getHeight();
            HdrExportState.width = w;
            HdrExportState.height = h;
            HdrExportState.setPeakBrightness((float) FlashbackExportExtrasConfig.INSTANCE.hdrPeakBrightness);
            HdrExportState.activate();
            FlashbackExportExtras.LOGGER.info("HDR export: {}x{} peak={}nits", w, h, HdrExportState.getPeakBrightness());
        }
        /*?}*/

        if (FlashbackExportExtrasConfig.INSTANCE.exportCameraPath) {
            float aspectRatio = (float) settings.resolutionX() / (float) settings.resolutionY();
            cameraExporter = new CameraPathExporter(aspectRatio, settings.framerate(),
                    FlashbackExportExtrasConfig.INSTANCE.cameraPathRelativeOrigin);
        }
    }

    // === Reserve a depth frame before Flashback renders it ===

    @Redirect(method = "doExport",
            at = @At(value = "INVOKE",
                    target = "Lcom/moulberry/flashback/exporting/SaveableFramebufferQueue;take()Lcom/moulberry/flashback/exporting/SaveableFramebuffer;"),
            remap = false)
    private SaveableFramebuffer flashbackexportextras$reserveDepthFrame(
            SaveableFramebufferQueue downloader) {
        SaveableFramebuffer framebuffer = downloader.take();
        if (isExrMode) {
            long frameId = DepthCaptureState.reserveExportFrameId();
            FlashbackExportExtras.LOGGER.debug("Reserved depth capture frame {}", frameId);
        }
        return framebuffer;
    }

    // === Start color download after the matching depth capture ===

    @Redirect(method = "doExport",
            at = @At(value = "INVOKE",
                    target = "Lcom/moulberry/flashback/exporting/SaveableFramebufferQueue;startDownload(Lcom/mojang/blaze3d/pipeline/RenderTarget;Lcom/moulberry/flashback/exporting/SaveableFramebuffer;Z)V"),
            remap = false)
    private void flashbackexportextras$captureAndStartDownload(SaveableFramebufferQueue downloader,
                                                        RenderTarget target,
                                                        SaveableFramebuffer framebuffer,
                                                        boolean flag) {
        if (isExrMode) {
            long frameId = DepthCaptureState.submittedCaptureFrameId();
            if (frameId != flashbackexportextras_nextDepthColorFrameId
                    || !DepthCaptureState.wasCaptureSubmitted(frameId)) {
                throw new IllegalStateException("No matching depth capture completed before color download: "
                        + "expected=" + flashbackexportextras_nextDepthColorFrameId + ", actual=" + frameId);
            }
            if (isExrSceneLinearHdr) {
                GpuExportBackendFactory.get().captureSceneLinearHdr(
                        target, target.width, target.height, frameId);
            }
        }
        flashbackexportextras$captureHdrBeforeDownload(target);
        if (isExrMode) {
            // the camera of this frame, for the EXR header
            TranslucentCaptureState.putCamera(flashbackexportextras_nextDepthColorFrameId,
                    new TranslucentCaptureState.CameraInfo(DepthCaptureState.camX, DepthCaptureState.camY,
                            DepthCaptureState.camZ, DepthCaptureState.camYaw, DepthCaptureState.camPitch,
                            DepthCaptureState.fovDegrees));
        }
        downloader.startDownload(target, framebuffer, flag);
        if (isExrMode) {
            flashbackexportextras_nextDepthColorFrameId++;
        }
        flashbackexportextras$recordCameraFrame();
    }

    /** HDR capture shares the depth redirect so the two injectors cannot consume the same invocation. */
    @Unique
    private void flashbackexportextras$captureHdrBeforeDownload(RenderTarget target) {
        /*? if hdr {*/
        if (!isHdrMode) return;
        if (target == null) return;

        float peak = HdrExportState.getPeakBrightness();
        long frameId = flashbackexportextras_hdrCaptureFrameCount++;
        GpuExportBackendFactory.get().captureHdr(
                target, target.width, target.height, peak, frameId);
        flashbackexportextras$drainHdrFrames();

        // Step 1: Color transform — scRGB-nl → BT.2020 + PQ

        /*?}*/
    }

    @Unique
    private void flashbackexportextras$drainHdrFrames() {
        /*? if hdr {*/
        while (hdrWriterRef != null) {
            HdrVideoCaptureState.Frame frame =
                    HdrVideoCaptureState.poll(hdrWriterRef.getFrameCount());
            if (frame == null) break;
            hdrWriterRef.addHdrFrame(frame.frameId, frame.data);
        }
        /*?}*/
    }

    //? if >=1.21.5 {
    /*@Inject(method = "doExport",
            at = @At(value = "INVOKE",
                    target = "Lcom/moulberry/flashback/exporting/VideoWriter;finish(Ljava/util/function/Consumer;)V"),
            remap = false)
    private void flashbackexportextras$flushGpuBeforeFinish(VideoWriter videoWriter,
                                                     SaveableFramebufferQueue downloader,
                                                     CallbackInfo ci) {
        flashbackexportextrasFlushGpuReadback();
    }
    *///?} else {
    @Inject(method = "doExport",
            at = @At(value = "INVOKE",
                    target = "Lcom/moulberry/flashback/exporting/VideoWriter;finish()V"),
            remap = false)
    private void flashbackexportextras$flushGpuBeforeFinish(VideoWriter videoWriter,
                                                     SaveableFramebufferQueue downloader,
                                                     CallbackInfo ci) {
        flashbackexportextrasFlushGpuReadback();
    }
    //?}

    @Unique
    private void flashbackexportextrasFlushGpuReadback() {
        if (!isExrMode && !isHdrMode) return;
        FlashbackExportExtras.LOGGER.info("Export GPU flush started: exr={}, hdr={}", isExrMode, isHdrMode);
        GpuExportBackendFactory.get().flush();
        //? if >=26.3 {
        /*if (isExrTranslucentLayer) {
            com.rethinkqaq.flashbackexportextras.gpu.TranslucentExportCapture.INSTANCE.flush();
        }
        *///?}
        /*? if hdr {*/
        if (isHdrMode) {
            flashbackexportextras$drainHdrFrames();
            long written = hdrWriterRef == null ? 0L : hdrWriterRef.getFrameCount();
            HdrVideoCaptureState.verifyComplete(flashbackexportextras_hdrCaptureFrameCount, written);
        }
        /*?}*/
        FlashbackExportExtras.LOGGER.info("Export GPU flush completed");
    }

    // === Camera capture: called immediately AFTER startDownload ===

    @Unique
    private void flashbackexportextras$recordCameraFrame() {
        if (cameraExporter == null) return;
        Vec3 pos = new Vec3(DepthCaptureState.camX, DepthCaptureState.camY, DepthCaptureState.camZ);
        cameraExporter.recordFrame(pos, DepthCaptureState.camYaw, DepthCaptureState.camPitch,
                DepthCaptureState.keyframeTargetFov);
    }

    // === Redirect VideoWriter.encode ===

    /*? if >=26.1 {*/
    /*@Redirect(method = "submitDownloadedFrames",
            at = @At(value = "INVOKE",
                    target = "Lcom/moulberry/flashback/exporting/VideoWriter;encode(Lcom/moulberry/flashback/exporting/ImageFrame;)V"),
            remap = false)
    private void onVideoEncode(VideoWriter videoWriter, ImageFrame frame) {
        if (isHdrMode) {
            // HDR frames arrive through the asynchronous GPU readback stream;
            // release Flashback's normal SDR frame immediately.
            frame.close();
        } else {
            videoWriter.encode(frame);
        }
    }
    *//*?}*/

    //? if <26.1 {
    @Redirect(method = "submitDownloadedFrames",
            at = @At(value = "INVOKE",
                    target = "Lcom/moulberry/flashback/exporting/VideoWriter;encode(Lcom/mojang/blaze3d/platform/NativeImage;Ljava/nio/FloatBuffer;)V"),
            remap = false)
    private void onVideoEncode(VideoWriter videoWriter, NativeImage image, FloatBuffer audioBuffer) {
        if (isHdrMode) {
            // Normal pipeline's NativeImage is unused in HDR mode.
            image.close();
        } else {
            videoWriter.encode(image, audioBuffer);
        }
    }
    //?}

    // === Export session finalization ===

    @Redirect(method = "run",
            at = @At(value = "INVOKE",
                    target = "Lcom/moulberry/flashback/exporting/ExportJob;doExport(Lcom/moulberry/flashback/exporting/VideoWriter;Lcom/moulberry/flashback/exporting/SaveableFramebufferQueue;)V"),
            remap = false)
    private void flashbackexportextras$runExportSession(ExportJob instance, VideoWriter videoWriter,
                                                SaveableFramebufferQueue downloader) {
        boolean completed = false;
        try {
            doExport(videoWriter, downloader);
            completed = true;
        } finally {
            flashbackexportextras$finishExportSession(completed);
        }
    }

    @Unique
    private void flashbackexportextras$finishExportSession(boolean completed) {
        if (!flashbackexportextras_sessionActive) return;
        FlashbackExportExtras.LOGGER.info("Export session cleanup started: completed={}", completed);
        RuntimeException cameraExportFailure = null;
        if (completed && cameraExporter != null && cameraExporter.getFrameCount() > 0) {
            try {
                Path videoPath = settings.output();
                String videoName = videoPath.getFileName().toString();
                int dot = videoName.lastIndexOf('.');
                String base = dot > 0 ? videoName.substring(0, dot) : videoName;
                CameraPathExporter.Format cameraFormat = FlashbackExportExtrasConfig.INSTANCE.getCameraExportFormat();
                Path cameraPath = isExrMode
                        ? videoPath.resolve(cameraFormat.fileName())
                        : videoPath.resolveSibling(base + "_" + cameraFormat.fileStem() + "." + cameraFormat.extension());
                cameraExporter.finish(cameraPath, cameraFormat);
                FlashbackExportExtras.LOGGER.info("Camera path ({}) : {} frames → {}", cameraFormat,
                        cameraExporter.getFrameCount(), cameraPath);
            } catch (Exception e) {
                FlashbackExportExtras.LOGGER.error("Failed to finalize camera path", e);
                cameraExportFailure = new IllegalStateException("Failed to export selected camera path", e);
            }
        }

        flashbackexportextras$cleanupStep("HDR export state", () -> {
            /*? if hdr {*/
            HdrExportState.deactivate();
            /*?}*/
        });
        flashbackexportextras$cleanupStep("depth frame state", DepthCaptureState::reset);
        flashbackexportextras$cleanupStep("translucent layer state", () -> {
            TranslucentCaptureState.reset();
            //? if >=26.3 {
            /*com.rethinkqaq.flashbackexportextras.gpu.TranslucentExportCapture.INSTANCE.reset();
            *///?}
        });
        flashbackexportextras$cleanupStep("scene-linear HDR frame state", SceneLinearHdrCaptureState::reset);
        flashbackexportextras$cleanupStep("HDR10 frame state", () -> {
            /*? if hdr {*/
            HdrVideoCaptureState.reset();
            flashbackexportextras_hdrCaptureFrameCount = 0L;
            /*?}*/
        });
        flashbackexportextras$cleanupStep("GPU backend", GpuExportBackendFactory::reset);
        if (flashbackexportextras_dummyFramesOverridden) {
            flashbackexportextras$cleanupStep("Flashback warm-up frame setting", () ->
                    com.moulberry.flashback.Flashback.getConfig().exporting.exportRenderDummyFrames =
                            flashbackexportextras_originalDummyFrames);
            flashbackexportextras_dummyFramesOverridden = false;
        }
        cameraExporter = null;
        isExrMode = false;
        isExrSceneLinearHdr = false;
        isExrTranslucentLayer = false;
        isHdrMode = false;
        flashbackexportextras_nextDepthColorFrameId = 0L;
        /*? if hdr {*/
        hdrWriterRef = null;
        /*?}*/
        flashbackexportextras_sessionActive = false;
        FlashbackExportExtras.LOGGER.info("Export session cleanup completed");
        if (cameraExportFailure != null) throw cameraExportFailure;
    }

    @Unique
    private void flashbackexportextras$cleanupStep(String name, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (Throwable e) {
            FlashbackExportExtras.LOGGER.error("Export cleanup step failed: {}", name, e);
        }
    }
}
