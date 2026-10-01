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
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
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
/*? if >=26.1 {*/
/*import org.spongepowered.asm.mixin.gen.Accessor;
*//*?}*/

@Restriction(require = @Condition(value = "minecraft", versionPredicates = ">=26.1"))
/*? if >=26.1 {*/
/*@Mixin(targets = "com.moulberry.flashback.exporting.ExportJob$TempFileInfo", remap = false)
*//*?} else {*/
@Mixin(Dummy.class)
/*?}*/
public interface ExportJobTempFileInfoAccess {
    /*? if >=26.1 {*/
    /*@Accessor("name")
    String flashbackexportextras$getName();
    *//*?}*/
}
