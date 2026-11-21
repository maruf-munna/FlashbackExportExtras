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

import com.moulberry.flashback.exporting.ExportSettings;
import com.rethinkqaq.flashbackexportextras.FlashbackExportExtrasConfig;
import com.rethinkqaq.flashbackexportextras.FlashbackExportExtrasConfig.ExrCompression;

import java.io.IOException;
import java.nio.file.Path;

/** Pass settings saved with a queued 3D Render job. */
public record Render3dOptions(String folderName, boolean depth, boolean linearize, int depthBits,
                             int mistDistance, boolean normal, boolean translucentDepth, boolean wave,
                             ExrCompression compression) {
    public static Render3dOptions capture(FlashbackExportExtrasConfig config) {
        return new Render3dOptions(config.render3dOutputName, config.exrExportDepth,
                config.depthLinearizeWorldSpace, config.exrDepthBits,
                config.exrExportMist ? Math.max(1, config.exrMistDistance) : 0,
                config.exrExportNormal, config.render3dTranslucentDepth, config.render3dExportAudio,
                config.getExrCompression());
    }

    public void validate(ExportSettings settings) throws IOException {
        if (settings.container().name().endsWith("_SEQUENCE")) {
            throw new IOException("3D Render needs a video container");
        }
        if (!depth && mistDistance == 0 && !normal) {
            throw new IOException("3D Render needs at least one EXR pass");
        }
        //? if flashback_encoder_name {
        /*if (!settings.projection().name().equals("PERSPECTIVE") || settings.depthMap()) {
            throw new IOException("3D Render needs a normal perspective video");
        }
        *///?}
    }

    public Path directoryFor(Path video) {
        Path absolute = video.toAbsolutePath().normalize();
        String stem = absolute.getFileName().toString();
        int extension = stem.lastIndexOf('.');
        if (extension > 0) stem = stem.substring(0, extension);
        String name = folderName == null ? "" : folderName.strip().replaceAll("[^A-Za-z0-9._+-]", "_");
        if (name.isBlank() || name.equals(".") || name.equals("..")) name = stem + "_exr";
        return absolute.resolveSibling(name);
    }
}
