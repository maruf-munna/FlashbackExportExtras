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
package com.rethinkqaq.flashbackexportextras;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;

public class FlashbackExportExtras implements ModInitializer, ClientModInitializer {
    public static final String MOD_ID = "flashbackexportextras";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    /** Export Transparent (Trans / TransDepth EXR layers) needs 26.3's OIT hooks. */
    //? if >=26.3 {
    /*public static final boolean TRANSLUCENT_LAYER_SUPPORTED = true;
    *///?} else {
    public static final boolean TRANSLUCENT_LAYER_SUPPORTED = false;
    //?}

    @Override public void onInitialize() { LOGGER.info("Flashback Export Extras initializing..."); }

    @Override public void onInitializeClient() {
        LOGGER.info("Flashback Export Extras client initializing...");
        FlashbackExportExtrasConfig.load();
        LOGGER.info("HDR integration is provided by the optional HDR Mixin");
    }
}
