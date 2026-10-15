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
/*import com.rethinkqaq.flashbackexportextras.exporting.TranslucentCaptureState;
import com.rethinkqaq.flashbackexportextras.gpu.HudExportCapture;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}
import org.spongepowered.asm.mixin.Mixin;

/** Export Transparent HUD layer (26.3): after the HUD is drawn, it is drawn once more into the UI layer target. */
//? if >=26.3 {
/*@Mixin(value = GuiRenderer.class, remap = false)
*///?} else {
@Mixin(Dummy.class)
//?}
public class MixinGuiRendererHud {
    //? if >=26.3 {
    /*@Inject(method = "draw", at = @At("RETURN"), remap = false)
    private void flashbackexportextras$captureHudLayer(CallbackInfo ci) {
        if (!TranslucentCaptureState.hudActive) {
            return;
        }
        GuiRendererHudAccessor self = (GuiRendererHudAccessor) this;
        HudExportCapture.INSTANCE.capture(self, self.flashbackexportextras$getDraws().size());
    }
    *///?}
}
