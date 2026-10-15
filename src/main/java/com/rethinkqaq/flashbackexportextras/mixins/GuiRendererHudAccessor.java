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
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.function.Supplier;
*///?}
import org.spongepowered.asm.mixin.Mixin;

/** Export Transparent HUD layer (26.3): the HUD draw list of the frame and the call that draws a range of it. */
//? if >=26.3 {
/*@Mixin(value = GuiRenderer.class, remap = false)
*///?} else {
@Mixin(Dummy.class)
//?}
public interface GuiRendererHudAccessor {
    //? if >=26.3 {
    /*// raw List: GuiRenderer.Draw is private
    @Accessor(value = "draws", remap = false)
    List<?> flashbackexportextras$getDraws();

    @Invoker(value = "executeDrawRange", remap = false)
    void flashbackexportextras$executeDrawRange(Supplier<String> label, RenderTarget target,
            GpuBufferSlice dynamicTransforms, int startIndex, int endIndex);
    *///?}
}
