/*
 *       _____  _       _    _____                                _
 *      |  __ \| |     | |  / ____|                              | |
 *      | |__) | | ___ | |_| (___   __ _ _   _  __ _ _ __ ___  __| |
 *      |  ___/| |/ _ \| __|\___ \ / _` | | | |/ _` | '__/ _ \/ _` |
 *      | |    | | (_) | |_ ____) | (_| | |_| | (_| | | |  __/ (_| |
 *      |_|    |_|\___/ \__|_____/ \__, |\__,_|\__,_|_|  \___|\__,_|
 *                                    | |
 *                                    |_|
 *            PlotSquared plot management system for Minecraft
 *                  Copyright (C) 2020 IntellectualSites
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.plotsquared.bukkit.util;

import com.google.inject.Singleton;
import com.plotsquared.core.util.ChunkManager;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.regions.CuboidRegion;
import io.papermc.lib.PaperLib;

import java.util.concurrent.CompletableFuture;

@Singleton
public class BukkitChunkManager extends ChunkManager {

    public static boolean isIn(CuboidRegion region, int x, int z) {
        return x >= region.getMinimumPoint().getX() && x <= region.getMaximumPoint().getX() && z >= region.getMinimumPoint().getZ() && z <= region
            .getMaximumPoint().getZ();
    }

    @Override public CompletableFuture<?> loadChunk(String world, BlockVector2 chunkLoc, boolean force) {
        return PaperLib.getChunkAtAsync(BukkitUtil.getWorld(world), chunkLoc.getX(), chunkLoc.getZ(), force);
    }

}
