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
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Iris must test the replay player's game mode, as Flashback's vanilla hand renderer does. */
@Restriction(require = @Condition(value = "iris", versionPredicates = {"=1.11.7", "=1.10.7"}))
@Mixin(targets = "net.irisshaders.iris.pathways.HandRenderer", remap = false)
public abstract class MixinIrisHandRenderer {
    @WrapOperation(method = "canRender", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;"),
            require = 0)
    private GameType flashbackexportextras$replayGameMode(MultiPlayerGameMode gameMode, Operation<GameType> original) {
        var spectated = Flashback.getSpectatingPlayer();
        if (spectated == null) return original.call(gameMode);
        return spectated.isSpectator() ? GameType.SPECTATOR : GameType.SURVIVAL;
    }
}
