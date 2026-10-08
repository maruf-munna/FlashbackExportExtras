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
/*? if >=1.21.5 {*/
/*import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
*//*?}*/
import com.rethinkqaq.flashbackexportextras.FlashbackExportExtras;
import com.rethinkqaq.flashbackexportextras.exporting.DepthCaptureState;
import com.rethinkqaq.flashbackexportextras.gpu.GpuExportBackendFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
/*? if >=26.2 {*/
/*import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
*//*?}*/
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures camera metadata and delegates version-specific depth work to the GPU backend. */
@Mixin(value = GameRenderer.class, remap = false)
public class MixinGameRenderer {
    /*? if >=26.2 {*/
    /*@Shadow @Final private GameRenderState gameRenderState;
    // the depth attachment cleared right before the first-person hand (the hand is drawn into it)
    @Unique private GpuTexture flashbackexportextras_handDepthTexture;
    *//*?}*/
    @Unique
    private boolean flashbackexportextras_cameraCaptureFailedLogged;

    /*? if >=26.3 {*/
    /*// 26.3 moved the world-depth clear (before the hand renders) into render3dHud.
    @Redirect(method = "render3dHud",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"),
            remap = false)
    private void flashbackexportextras$redirectClearDepthTexture(CommandEncoder encoder, GpuTexture texture, double depth) {
        flashbackexportextras$capturePendingDepthBeforeClear(encoder);
        // main depth, or the 3D HUD target when post effects are on: the hand is drawn into this one
        flashbackexportextras_handDepthTexture = texture;
        encoder.clearDepthTexture(texture, depth);
    }

    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"),
            remap = false)
    private void flashbackexportextras$preserveDepthDuringGui(CommandEncoder encoder, GpuTexture texture, double depth) {
        encoder.clearDepthTexture(texture, depth);
    }
    *//*?} elif >=26.2 {*/
    /*@Redirect(method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"),
            remap = false)
    private void flashbackexportextras$redirectClearDepthTexture(CommandEncoder encoder, GpuTexture texture, double depth) {
        flashbackexportextras$capturePendingDepthBeforeClear(encoder);
        // the main depth: the hand is drawn into it right after this clear
        flashbackexportextras_handDepthTexture = texture;
        encoder.clearDepthTexture(texture, depth);
    }

    @Redirect(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"),
            remap = false)
    private void flashbackexportextras$preserveDepthDuringGui(CommandEncoder encoder, GpuTexture texture, double depth) {
        encoder.clearDepthTexture(texture, depth);
    }
    *//*?} elif >=26.1 {*/
    /*@Redirect(method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"),
            remap = false)
    private void flashbackexportextras$redirectClearDepthTexture(CommandEncoder encoder, GpuTexture texture, double depth) {
        flashbackexportextras$capturePendingDepthBeforeClear(encoder);
        encoder.clearDepthTexture(texture, depth);
    }
    *//*?} elif >=1.21.5 {*/
    /*@Redirect(method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"),
            remap = false)
    private void flashbackexportextras$redirectClearDepthTexture(CommandEncoder encoder, GpuTexture texture, double depth) {
        flashbackexportextras$capturePendingDepthBeforeClear(encoder);
        encoder.clearDepthTexture(texture, depth);
    }
    *//*?} elif >=1.21.4 {*/
    /*@Redirect(method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(I)V"),
            remap = false)
    private void flashbackexportextras$redirectClearInRenderLevel(int mask) {
        if ((mask & 256) != 0) flashbackexportextras$capturePendingDepthBeforeClear(null);
        com.mojang.blaze3d.systems.RenderSystem.clear(mask);
    }
    *//*?} else {*/
    @Redirect(method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"),
            remap = false)
    private void flashbackexportextras$redirectClearInRenderLevel(int mask, boolean getError) {
        if ((mask & 256) != 0) flashbackexportextras$capturePendingDepthBeforeClear(null);
        com.mojang.blaze3d.systems.RenderSystem.clear(mask, getError);
    }
    /*?}*/

    @Inject(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
                    shift = At.Shift.AFTER),
            remap = false)
    private void flashbackexportextras$captureCamera(CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getInstance();
            /*? if >=26.2 {*/
            /*var camera = mc.gameRenderer.mainCamera();
            *//*?} else {*/
            var camera = mc.gameRenderer.getMainCamera();
            /*?}*/
            if (camera != null && camera.isInitialized()) {
                /*? if >=1.21.11 {*/
                /*var pos = camera.position();
                *//*?} else {*/
                var pos = camera.getPosition();
                /*?}*/
                DepthCaptureState.camX = pos.x;
                DepthCaptureState.camY = pos.y;
                DepthCaptureState.camZ = pos.z;
                /*? if >=1.21.11 {*/
                /*DepthCaptureState.camYaw = camera.yRot();
                DepthCaptureState.camPitch = camera.xRot();
                *//*?} else {*/
                DepthCaptureState.camYaw = camera.getYRot();
                DepthCaptureState.camPitch = camera.getXRot();
                /*?}*/
            }
            flashbackexportextras_cameraCaptureFailedLogged = false;
        } catch (Exception e) {
            if (!flashbackexportextras_cameraCaptureFailedLogged) {
                flashbackexportextras_cameraCaptureFailedLogged = true;
                FlashbackExportExtras.LOGGER.error("Failed to capture camera data for camera-path export", e);
            }
        }
    }

    /*? if >=26.2 {*/
    /*// renderLevel has drawn the first-person hand into the depth cleared for it: the hand's pixels of this frame's
    // depth capture become the near plane (nothing composited by depth passes the hand), then it is read back.
    @Inject(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
                    shift = At.Shift.AFTER),
            remap = false)
    private void flashbackexportextras$overlayHandDepth(CallbackInfo ci) {
        GpuTexture hand = flashbackexportextras_handDepthTexture;
        flashbackexportextras_handDepthTexture = null;
        GpuExportBackendFactory.captureHandDepthOnRenderThread(hand);
    }
    *//*?}*/

    @Unique
    private void flashbackexportextras$capturePendingDepthBeforeClear(Object clearEncoder) {
        if (!DepthCaptureState.active) return;
        long frameId = DepthCaptureState.pendingCaptureFrameId();
        if (frameId < 0L) return;
        Minecraft mc = Minecraft.getInstance();
        /*? if >=26.2 {*/
        /*RenderTarget target = mc.gameRenderer.mainRenderTarget();
        *//*?} else {*/
        RenderTarget target = mc.getMainRenderTarget();
        /*?}*/
        if (target == null || !target.useDepth) {
            DepthCaptureState.failPendingCapture(
                    new IllegalStateException("Main render target has no depth attachment"));
            throw new IllegalStateException("Cannot capture depth for export frame " + frameId);
        }
        try {
            /*? if >=26.2 {*/
            /*CameraRenderState cameraState = gameRenderState.levelRenderState.cameraRenderState;
            if (cameraState != null && Float.isFinite(cameraState.depthFar) && cameraState.depthFar > 0.05f) {
                DepthCaptureState.depthFar = cameraState.depthFar;
            }
            *//*?}*/
            // Iris depthtex2 is a pre-hand snapshot.  Iris calls beginHand
            // before translucent world rendering, so it is not a complete
            // scene depth source. Always capture Minecraft's final main
            // RenderTarget immediately before its world-depth clear instead.
            GpuExportBackendFactory.get().captureDepthBeforeClear(clearEncoder,
                    target, DepthCaptureState.width, DepthCaptureState.height,
                    DepthCaptureState.depthFar, frameId);
            DepthCaptureState.markCaptureSubmitted(frameId);
        } catch (RuntimeException e) {
            DepthCaptureState.failPendingCapture(e);
            throw e;
        }
    }

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void flashbackexportextras$releasePendingGpuResources(CallbackInfo ci) {
        DepthCaptureState.beginRenderFrame();
        GpuExportBackendFactory.releasePendingOnRenderThread();
    }

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void flashbackexportextras$collectGpuReadbacks(CallbackInfo ci) {
        GpuExportBackendFactory.endFrameOnRenderThread();
    }
}
