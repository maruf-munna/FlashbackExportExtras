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
/*import com.rethinkqaq.flashbackexportextras.gpu.HudExportCapture;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}
import org.spongepowered.asm.mixin.Mixin;

/**
 * HUD layer replay (26.3): "over" blends write alpha as (SRC_ALPHA, ONE_MINUS_SRC_ALPHA) = a squared on a transparent
 * target (a 50 % chat background ends up 25 %). Only while the HUD layer is drawn, alpha becomes real premultiplied
 * coverage (ONE, ONE_MINUS_SRC_ALPHA); additive blends keep alpha. On screen alpha is never read, so nothing else changes.
 */
//? if >=26.3 {
/*@Mixin(value = GlStateManager.class, remap = false)
*///?} else {
@Mixin(Dummy.class)
//?}
public abstract class MixinGlStateManagerHudBlend {
    //? if >=26.3 {
    /*@Inject(method = "_blendFuncSeparate", at = @At("HEAD"), cancellable = true, remap = false)
    private static void flashbackexportextras$premultipliedHudAlpha(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha,
            CallbackInfo ci) {
        if (!HudExportCapture.passActive) {
            return;
        }
        int fixedSrc = srcAlpha;
        int fixedDst = dstAlpha;
        if (dstRgb == GL11.GL_ONE_MINUS_SRC_ALPHA) {
            fixedSrc = GL11.GL_ONE;
            fixedDst = GL11.GL_ONE_MINUS_SRC_ALPHA;
        } else if (dstRgb == GL11.GL_ONE && (srcRgb == GL11.GL_SRC_ALPHA || srcRgb == GL11.GL_ONE)) {
            fixedSrc = GL11.GL_ZERO;
            fixedDst = GL11.GL_ONE;
        }
        if (fixedSrc != srcAlpha || fixedDst != dstAlpha) {
            ci.cancel();
            GlStateManager._blendFuncSeparate(srcRgb, dstRgb, fixedSrc, fixedDst);
        }
    }
    *///?}
}
