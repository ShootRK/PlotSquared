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
package com.plotsquared.core.command;

import com.google.inject.Inject;
import com.plotsquared.core.backup.BackupManager;
import com.plotsquared.core.configuration.Settings;
import com.plotsquared.core.configuration.caption.StaticCaption;
import com.plotsquared.core.configuration.caption.TranslatableCaption;
import com.plotsquared.core.permissions.Permission;
import com.plotsquared.core.player.PlotPlayer;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.PlotArea;
import com.plotsquared.core.plot.PlotManager;
import com.plotsquared.core.queue.QueueCoordinator;
import com.plotsquared.core.util.PatternUtil;
import com.plotsquared.core.util.Permissions;
import com.plotsquared.core.util.StringMan;
import com.plotsquared.core.util.TabCompletions;
import com.plotsquared.core.util.WorldUtil;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.world.block.BlockCategory;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.kyori.adventure.text.minimessage.Template;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CommandDeclaration(command = "set",
    description = "Set a plot value",
    aliases = {"s"},
    usage = "/plot set <biome | alias | home | flag> <value...>",
    permission = "plots.set",
    category = CommandCategory.APPEARANCE,
    requiredType = RequiredType.NONE)
public class Set extends SubCommand {

    public static final String[] values = new String[] {"biome", "alias", "home"};
    public static final String[] aliases = new String[] {"b", "w", "wf", "a", "h"};

    private final SetCommand component;

    @Inject public Set(@Nonnull final WorldUtil worldUtil) {
        this.component = new SetCommand() {

            @Override public String getId() {
                return "set.component";
            }

            @Override public boolean set(PlotPlayer player, final Plot plot, String value) {
                final PlotArea plotArea = player.getLocation().getPlotArea();
                if (plotArea == null) {
                    return false;
                }
                final PlotManager manager = plotArea.getPlotManager();

                String[] components = manager.getPlotComponents(plot.getId());

                String[] args = value.split(" ");
                String material =
                    StringMan.join(Arrays.copyOfRange(args, 1, args.length), ",").trim();

                final List<String> forbiddenTypes = new ArrayList<>(Settings.General.INVALID_BLOCKS);

                if (Settings.Enabled_Components.CHUNK_PROCESSOR) {
                    forbiddenTypes.addAll(worldUtil.getTileEntityTypes().stream().map(
                        BlockType::getName).collect(Collectors.toList()));
                }

                if (!Permissions.hasPermission(player, Permission.PERMISSION_ADMIN_ALLOW_UNSAFE) &&
                    !forbiddenTypes.isEmpty()) {
                    for (String forbiddenType : forbiddenTypes) {
                        forbiddenType = forbiddenType.toLowerCase(Locale.ENGLISH);
                        if (forbiddenType.startsWith("minecraft:")) {
                            forbiddenType = forbiddenType.substring(10);
                        }
                        for (String blockType : material.split(",")) {
                            blockType = blockType.toLowerCase(Locale.ENGLISH);
                            if (blockType.startsWith("minecraft:")) {
                                blockType = blockType.substring(10);
                            }

                            if (blockType.startsWith("##")) {
                                try {
                                    final BlockCategory category = BlockCategory.REGISTRY.get(blockType.substring(2)
                                        .replaceAll("[*^|]+", "").toLowerCase(Locale.ENGLISH));
                                    if (category == null || !category.contains(BlockTypes.get(forbiddenType))) {
                                        continue;
                                    }
                                } catch (final Throwable ignored) {
                                }
                            } else if (!blockType.contains(forbiddenType)) {
                                continue;
                            }
                            player.sendMessage(
                                    TranslatableCaption.of("invalid.component_illegal_block"),
                                    Template.of("value", forbiddenType)
                            );
                            return true;
                        }
                    }
                }

                for (String component : components) {
                    if (component.equalsIgnoreCase(args[0])) {
                        if (!Permissions.hasPermission(player, Permission.PERMISSION_SET_COMPONENT.format(component))) {
                            player.sendMessage(TranslatableCaption.of("permission.no_permission"),
                                Template.of("node", Permission.PERMISSION_SET_COMPONENT.format(component)));
                            return false;
                        }
                        if (args.length < 2) {
                            player.sendMessage(TranslatableCaption.of("need.need_block"));
                            return true;
                        }

                        Pattern pattern = PatternUtil.parse(player, material, false);

                        if (plot.getRunning() > 0) {
                            player.sendMessage(TranslatableCaption.of("errors.wait_for_timer"));
                            return false;
                        }

                        BackupManager.backup(player, plot, () -> {
                            plot.addRunning();
                            QueueCoordinator queue = plotArea.getQueue();
                            for (Plot current : plot.getConnectedPlots()) {
                                current.setComponent(component, pattern, queue);
                            }
                            queue.setCompleteTask(plot::removeRunning);
                            queue.enqueue();
                            player.sendMessage(TranslatableCaption.of("working.generating_component"));
                        });
                        return true;
                    }
                }
                return false;
            }

            @Override
            public Collection<Command> tab(final PlotPlayer player, final String[] args,
                final boolean space) {
                return TabCompletions.completePatterns(StringMan.join(args, ","));
            }
        };
    }

    public boolean noArgs(PlotPlayer player) {
        ArrayList<String> newValues = new ArrayList<>(Arrays.asList("biome", "alias", "home"));
        Plot plot = player.getCurrentPlot();
        if (plot != null) {
            newValues.addAll(Arrays.asList(plot.getManager().getPlotComponents(plot.getId())));
        }
        player.sendMessage(StaticCaption.of(TranslatableCaption.of("commandconfig.subcommand_set_options_header").getComponent(player) + StringMan
            .join(newValues, TranslatableCaption.of("blocklist.block_list_separator").getComponent(player))));
        return false;
    }

    @Override public boolean onCommand(PlotPlayer<?> player, String[] args) {
        if (args.length == 0) {
            return noArgs(player);
        }
        Command cmd = MainCommand.getInstance().getCommand("set" + args[0]);
        if (cmd != null) {
            if (!Permissions.hasPermission(player, cmd.getPermission(), true)) {
                return false;
            }
            cmd.execute(player, Arrays.copyOfRange(args, 1, args.length), null, null);
            return true;
        }
        // Additional checks
        Plot plot = player.getCurrentPlot();
        if (plot == null) {
            player.sendMessage(TranslatableCaption.of("errors.not_in_plot"));
            return false;
        }
        // components
        HashSet<String> components =
            new HashSet<>(Arrays.asList(plot.getManager().getPlotComponents(plot.getId())));
        if (components.contains(args[0].toLowerCase())) {
            return this.component.onCommand(player, Arrays.copyOfRange(args, 0, args.length));
        }
        return noArgs(player);
    }

    @Override
    public Collection<Command> tab(final PlotPlayer player, final String[] args,
        final boolean space) {
        if (args.length == 1) {
            return Stream
                .of("biome", "alias", "home", "main", "floor", "air", "all", "border", "wall",
                    "outline", "middle")
                .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ENGLISH)))
                .map(value -> new Command(null, false, value, "", RequiredType.NONE, null) {
                }).collect(Collectors.toList());
        } else if (args.length > 1) {
            // Additional checks
            Plot plot = player.getCurrentPlot();
            if (plot == null) {
                return new ArrayList<>();
            }

            final String[] newArgs = new String[args.length - 1];
            System.arraycopy(args, 1, newArgs, 0, newArgs.length);

            final Command cmd = MainCommand.getInstance().getCommand("set" + args[0]);
            if (cmd != null) {
                if (!Permissions.hasPermission(player, cmd.getPermission(), true)) {
                    return new ArrayList<>();
                }
                return cmd.tab(player, newArgs, space);
            }

            // components
            HashSet<String> components =
                new HashSet<>(Arrays.asList(plot.getManager().getPlotComponents(plot.getId())));
            if (components.contains(args[0].toLowerCase())) {
                return this.component.tab(player, newArgs, space);
            }
        }
        return tabOf(player, args, space);
    }
}
