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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Optional bridge to FlashPlus's recorded audio timeline. */
public final class FlashPlusAudioBridge {
    private static final Method HAS_TRACKS;
    private static final Method FINISH;

    static {
        Method hasTracks = null;
        Method finish = null;
        try {
            Class<?> owner = Class.forName("redsmods.flashplus.scene.RecordedTakeExport");
            finish = owner.getMethod("finishExrAudio", Path.class, long.class, Consumer.class);
            hasTracks = owner.getMethod("replacesGameAudio");
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // EXR can use Flashback's positional audio without FlashPlus.
        }
        HAS_TRACKS = hasTracks;
        FINISH = finish;
    }

    private FlashPlusAudioBridge() { }

    public static boolean hasRecordedTracks() {
        return HAS_TRACKS != null && invoke(HAS_TRACKS);
    }

    static boolean finish(Path output, long frames, Consumer<String> progress) {
        return FINISH != null && invoke(FINISH, output, frames, progress);
    }

    private static boolean invoke(Method method, Object... arguments) {
        try {
            return Boolean.TRUE.equals(method.invoke(null, arguments));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException failure) throw failure;
            if (e.getCause() instanceof Error failure) throw failure;
            throw new IllegalStateException("FlashPlus audio bridge failed", e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("FlashPlus audio bridge failed", e);
        }
    }
}
