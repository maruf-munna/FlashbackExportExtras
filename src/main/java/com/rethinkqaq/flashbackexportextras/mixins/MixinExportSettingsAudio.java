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

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.combo_options.VideoContainer;
import com.moulberry.flashback.exporting.ExportSettings;
import com.rethinkqaq.flashbackexportextras.FlashbackExportExtrasConfig;
import com.rethinkqaq.flashbackexportextras.exporting.FlashPlusAudioBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** EXR uses the same OpenAL device switch as a regular Flashback video export. */
@Mixin(value = ExportSettings.class, remap = false)
public abstract class MixinExportSettingsAudio {
    @Inject(method = "recordAudio", at = @At("RETURN"), cancellable = true)
    private void flashbackexportextras$enableExrLoopback(CallbackInfoReturnable<Boolean> callback) {
        var settings = (ExportSettings) (Object) this;
        var config = FlashbackExportExtrasConfig.INSTANCE;
        if (Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings() == settings
                && settings.container() == VideoContainer.PNG_SEQUENCE
                && config.getExportMode() == FlashbackExportExtrasConfig.ExportMode.EXR) {
            callback.setReturnValue(config.exrExportAudio && !FlashPlusAudioBridge.hasRecordedTracks());
        }
    }
}
