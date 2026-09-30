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
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.spongepowered.asm.mixin.Mixin;
/*? if <26.3 {*/
import com.moulberry.flashback.exporting.AsyncFileDialogs;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.ExecutorService;
/*?}*/

/**
 * Gives the client shutdown hook access to Flashback's file-dialog executor.
 * Flashback for 26.3 no longer has that executor, so the mixin is disabled there.
 */
@Restriction(require = @Condition(value = "minecraft", versionPredicates = "<26.3"))
/*? if <26.3 {*/
@Mixin(value = AsyncFileDialogs.class, remap = false)
/*?} else {*/
/*@Mixin(Dummy.class)
*//*?}*/
public interface AsyncFileDialogsAccessor {
    /*? if <26.3 {*/
    @Accessor("dialogThread")
    static ExecutorService flashbackexportextras$getDialogThread() {
        throw new AssertionError();
    }
    /*?}*/
}
