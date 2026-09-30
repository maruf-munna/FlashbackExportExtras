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

import net.minecraft.client.Minecraft;
import com.rethinkqaq.flashbackexportextras.FlashbackExportExtras;
import com.rethinkqaq.flashbackexportextras.exporting.DepthCaptureState;
import com.rethinkqaq.flashbackexportextras.exporting.SceneLinearHdrCaptureState;
/*? if hdr {*/
import com.rethinkqaq.flashbackexportextras.exporting.HdrVideoCaptureState;
/*?}*/
import com.rethinkqaq.flashbackexportextras.gpu.GpuExportBackendFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevent Flashback's non-daemon dialog executor from holding the JVM open. */
@Mixin(value = Minecraft.class, remap = false)
public class MixinMinecraftShutdown {
    @Inject(method = "close", at = @At("HEAD"), remap = false)
    private void flashbackexportextras$shutdownFlashbackDialogExecutor(CallbackInfo ci) {
        //? if <26.3 {
        try {
            AsyncFileDialogsAccessor.flashbackexportextras$getDialogThread().shutdownNow();
        } catch (Throwable t) {
            FlashbackExportExtras.LOGGER.warn("Failed to stop Flashback file-dialog executor during shutdown", t);
        }
        //?}
        try {
            DepthCaptureState.reset();
            SceneLinearHdrCaptureState.reset();
            /*? if hdr {*/
            HdrVideoCaptureState.reset();
            /*?}*/
            GpuExportBackendFactory.reset();
            // Minecraft.close runs before the render device is torn down. Drain
            // resources now; unfinished fences stay queued rather than being
            // accessed from an arbitrary worker thread.
            GpuExportBackendFactory.releasePendingOnRenderThread();
        } catch (Throwable t) {
            FlashbackExportExtras.LOGGER.warn("Failed to clean Flashback Export Extras GPU resources during shutdown", t);
        }
    }
}
