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

import com.rethinkqaq.flashbackexportextras.utils.Dummy;
//? if >=26.3 {
/*import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.FilterMode;
import com.rethinkqaq.flashbackexportextras.FlashbackExportExtras;
import com.rethinkqaq.flashbackexportextras.exporting.TranslucentCaptureState;
import com.rethinkqaq.flashbackexportextras.gpu.TranslucentExportCapture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.OptionalDouble;
*///?}
import org.spongepowered.asm.mixin.Mixin;

/**
 * Export Transparent (26.3): the start of OIT snapshots the solid scene, its end draws the OIT composite a second
 * time into a cleared target = the translucent layer. The game's own picture is unchanged.
 */
//? if >=26.3 {
/*@Mixin(value = LevelRenderer.class, remap = false)
*///?} else {
@Mixin(Dummy.class)
//?}
public class MixinLevelRendererTranslucent {
    //? if >=26.3 {
    /*@Unique
    private static final Vector4f FLASHBACKEXPORTEXTRAS_TRANSPARENT = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);

    @Shadow
    @Final
    private LevelTargetBundle targets;

    @Inject(method = "executeOit", at = @At("HEAD"), remap = false)
    private void flashbackexportextras$beginTranslucentSplit(ChunkSectionsToRender chunkSectionsToRender,
            FeatureRenderDispatcher.PreparedFrame featureFrame, CallbackInfo ci) {
        if (TranslucentCaptureState.active) {
            TranslucentExportCapture.INSTANCE.beginOit(targets.main.get());
        }
    }

    @Inject(method = "executeOit", at = @At("TAIL"), remap = false)
    private void flashbackexportextras$drawTranslucentLayer(ChunkSectionsToRender chunkSectionsToRender,
            FeatureRenderDispatcher.PreparedFrame featureFrame, CallbackInfo ci) {
        if (!TranslucentCaptureState.active) {
            return;
        }
        RenderTarget layer = TranslucentExportCapture.INSTANCE.layerTargetForOit();
        if (layer == null) {
            return;
        }
        boolean ok = false;
        try {
            var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            try (RenderPass renderPass = RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(() -> "Flashback Export Extras translucent layer", layer.getColorTextureView(),
                            Optional.of(FLASHBACKEXPORTEXTRAS_TRANSPARENT), layer.getDepthTextureView(),
                            OptionalDouble.of(0.0))) {
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("Sampler0", targets.accumulate.get().getColorTextureView(), nearest);
                for (int i = 0; i < LevelRenderer.OIT_TRANSMITTANCE_TARGET_COUNT; i++) {
                    renderPass.setUniform("Coeff" + i, targets.transmittance.get(i).get().getColorTextureView(), nearest);
                }
                renderPass.setUniform("DepthBoundsSampler", targets.depthBounds.get().getColorTextureView(), nearest);
                renderPass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.OIT_COMPOSITE));
                renderPass.draw(3, 1, 0, 0);
            }
            ok = true;
        } catch (Throwable t) {
            FlashbackExportExtras.LOGGER.error("Export Transparent: could not draw the translucent layer", t);
        }
        TranslucentExportCapture.INSTANCE.finishOit(ok);
    }
    *///?}
}
