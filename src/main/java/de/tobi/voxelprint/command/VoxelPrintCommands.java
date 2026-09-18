package de.tobi.voxelprint.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import de.iani.cubesideutils.commands.ArgsParser;
import de.iani.cubesideutils.fabric.commands.CommandRouter;
import de.iani.cubesideutils.fabric.commands.CommandUtil;
import de.iani.cubesideutils.fabric.commands.SubCommand;
import de.tobi.voxelprint.config.VoxelPrintConfig;
import de.tobi.voxelprint.export.ExportResult;
import de.tobi.voxelprint.export.ExportService;
import de.tobi.voxelprint.selection.Selection;
import de.tobi.voxelprint.selection.SelectionAnchor;
import de.tobi.voxelprint.selection.SelectionManager;
import de.tobi.voxelprint.selection.SelectionState;
import de.tobi.voxelprint.selection.SelectionValidator;
import de.tobi.voxelprint.util.ChatUtil;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class VoxelPrintCommands {

    private static final String ROOT = "voxelprint";
    private static final String ALIAS = "vp";
    private static final double MAX_TARGET_DISTANCE = 128.0;

    private final SelectionManager selections;
    private final VoxelPrintConfig config;
    private final ExportService exports;
    private final Runnable openConfigScreen;

    public VoxelPrintCommands(SelectionManager selections,
                              VoxelPrintConfig config,
                              ExportService exports,
                              Runnable openConfigScreen) {
        this.selections = Objects.requireNonNull(selections, "selections");
        this.config = Objects.requireNonNull(config, "config");
        this.exports = Objects.requireNonNull(exports, "exports");
        this.openConfigScreen = Objects.requireNonNull(openConfigScreen, "openConfigScreen");
    }

    private SelectionValidator.Limits limits() {
        return new SelectionValidator.Limits(config.maxSelectionVolume(), config.maxSelectionEdge());
    }

    public void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        CommandRouter router = new CommandRouter();
        
        // Workaround for CubesideUtils tab-completion bug: root executor provides the list of subcommands.
        router.addCommandMapping(new SubCommand() {
            @Override
            public boolean onCommand(FabricClientCommandSource sender, String alias, String commandString, ArgsParser args) {
                return false; // False indicates syntax error -> router shows help
            }

            @Override
            public Collection<String> onTabComplete(FabricClientCommandSource sender, String alias, ArgsParser args) {
                if (args.remaining() <= 1) {
                    return Arrays.asList("pos1", "pos2", "hpos1", "hpos2", "status", "clear", "deselect", "config", "export");
                }
                return Collections.emptyList();
            }
        });

        router.addCommandMapping(new SubCommand() {
            @Override
            public boolean onCommand(FabricClientCommandSource sender, String alias, String commandString, ArgsParser args) {
                setPositionAtPlayer(sender, Corner.FIRST);
                return true;
            }
        }, "pos1");

        router.addCommandMapping(new SubCommand() {
            @Override
            public boolean onCommand(FabricClientCommandSource sender, String alias, String commandString, ArgsParser args) {
                setPositionAtPlayer(sender, Corner.SECOND);
                return true;
            }
        }, "pos2");

        router.addCommandMapping(new SubCommand() {
            @Override
            public boolean onCommand(FabricClientCommandSource sender, String alias, String commandString, ArgsParser args) {
                setPositionAtTarget(sender, Corner.FIRST);
                return true;
            }
        }, "hpos1");

        router.addCommandMapping(new SubCommand() {
            @Override
            public boolean onCommand(FabricClientCommandSource sender, String alias, String commandString, ArgsParser args) {
                setPositionAtTarget(sender, Corner.SECOND);
                return true;
            }
        }, "hpos2");

        router.addCommandMapping(new SubCommand() {
            @Override
            public boolean onCommand(FabricClientCommandSource sender, String alias, String commandString, ArgsParser args) {
                status(sender);
                return true;
            }
        }, "status");

        SubCommand clearCmd = new SubCommand() {
            @Override
            public boolean onCommand(FabricClientCommandSource sender, String alias, String commandString, ArgsParser args) {
                clear(sender);
                return true;
            }
        };
        router.addCommandMapping(clearCmd, "clear");
        router.addCommandMapping(clearCmd, "deselect");

        router.addCommandMapping(new SubCommand() {
            @Override
            public boolean onCommand(FabricClientCommandSource sender, String alias, String commandString, ArgsParser args) {
                openConfigScreen.run();
                return true;
            }
        }, "config");

        router.addCommandMapping(new SubCommand() {
            @Override
            public boolean onCommand(FabricClientCommandSource sender, String alias, String commandString, ArgsParser args) {
                if (!args.hasNext()) {
                    ChatUtil.error(sender, "voxelprint.error.invalid_name");
                    return true;
                }
                export(sender, args.getNext());
                return true;
            }
        }, "export");

        CommandUtil.registerCommand(dispatcher, ROOT, router);
        CommandUtil.registerCommand(dispatcher, ALIAS, router);
    }

    private int setPositionAtPlayer(FabricClientCommandSource source, Corner corner) {
        LocalPlayer player = source.getPlayer();
        return setPosition(source, player, corner, player.blockPosition());
    }

    private int setPositionAtTarget(FabricClientCommandSource source, Corner corner) {
        LocalPlayer player = source.getPlayer();
        Optional<BlockPos> target = targetedBlock(player);
        if (target.isEmpty()) {
            ChatUtil.error(source, "voxelprint.error.no_target_block", (int) MAX_TARGET_DISTANCE);
            return 0;
        }
        return setPosition(source, player, corner, target.get());
    }

    private static Optional<BlockPos> targetedBlock(LocalPlayer player) {
        HitResult hit = player.pick(MAX_TARGET_DISTANCE, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        return Optional.of(((BlockHitResult) hit).getBlockPos().immutable());
    }

    private int setPosition(FabricClientCommandSource source, LocalPlayer player, Corner corner, BlockPos position) {
        SelectionAnchor anchor = new SelectionAnchor(player.level().dimension(), position);

        switch (corner) {
            case FIRST -> selections.setFirst(anchor);
            case SECOND -> selections.setSecond(anchor);
        }

        config.debug("{} set to {} in {}", corner, anchor.position(), anchor.dimensionId());

        ChatUtil.success(source, corner.messageKey(),
                anchor.position().getX(),
                anchor.position().getY(),
                anchor.position().getZ(),
                anchor.dimensionId());
        return Command.SINGLE_SUCCESS;
    }

    private int status(FabricClientCommandSource source) {
        SelectionState state = selections.get();
        SelectionValidator.Result result = SelectionValidator.validate(state, limits());

        ChatUtil.info(source, "voxelprint.status.header");
        ChatUtil.info(source, "voxelprint.status.dimension",
                source.getLevel().dimension().identifier().toString());
        reportCorner(source, 1, state.first().orElse(null));
        reportCorner(source, 2, state.second().orElse(null));

        result.selection().ifPresent(selection -> reportMeasurements(source, selection));

        if (result.isValid()) {
            ChatUtil.info(source, "voxelprint.status.valid");
        } else {
            reportProblem(source, result);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int clear(FabricClientCommandSource source) {
        if (selections.clear()) {
            ChatUtil.success(source, "voxelprint.selection.cleared");
        } else {
            ChatUtil.info(source, "voxelprint.selection.nothing_to_clear");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int export(FabricClientCommandSource source, String name) {
        SelectionState state = selections.get();
        SelectionValidator.Result validation = SelectionValidator.validate(state, limits());

        if (!validation.isValid()) {
            reportProblem(source, validation);
            return 0;
        }

        if (config.showExportNotifications()) {
            ChatUtil.info(source, "voxelprint.export.preparing");
        }

        Minecraft client = source.getClient();
        ClientLevel level = source.getLevel();
        ExportResult accepted = exports.start(client, level, validation.selection().orElseThrow(), name,
                reportFinished(client));

        if (accepted.status() != ExportResult.Status.ACCEPTED) {
            reportExportProblem(source, accepted.status());
            return 0;
        }

        if (config.showExportNotifications()) {
            ChatUtil.info(source, "voxelprint.export.snapshot_created");
        }
        return Command.SINGLE_SUCCESS;
    }

    private Consumer<ExportResult> reportFinished(Minecraft client) {
        return result -> {
            LocalPlayer player = client.player;
            if (player == null) {
                return;
            }
            switch (result.status()) {
                case SUCCESS -> {
                    if (config.showExportNotifications()) {
                        ChatUtil.success(player, "voxelprint.export.success", result.fileName());
                        ChatUtil.success(player, "voxelprint.export.location", result.location());
                    }
                }
                case FILE_EXISTS -> ChatUtil.error(player, "voxelprint.error.file_exists");
                default -> ChatUtil.error(player, "voxelprint.error.export_failed");
            }
        };
    }

    private static void reportExportProblem(FabricClientCommandSource source, ExportResult.Status status) {
        switch (status) {
            case INVALID_NAME -> ChatUtil.error(source, "voxelprint.error.invalid_name");
            case ALREADY_RUNNING -> ChatUtil.error(source, "voxelprint.error.export_running");
            case CHUNKS_NOT_LOADED -> ChatUtil.error(source, "voxelprint.error.chunks_not_loaded");
            case FILE_EXISTS -> ChatUtil.error(source, "voxelprint.error.file_exists");
            default -> ChatUtil.error(source, "voxelprint.error.export_failed");
        }
    }

    private static void reportCorner(FabricClientCommandSource source, int index, SelectionAnchor anchor) {
        if (anchor == null) {
            ChatUtil.info(source, "voxelprint.status.position_unset", index);
            return;
        }
        ChatUtil.info(source, "voxelprint.status.position", index,
                anchor.position().getX(),
                anchor.position().getY(),
                anchor.position().getZ(),
                anchor.dimensionId());
    }

    private static void reportMeasurements(FabricClientCommandSource source, Selection selection) {
        ChatUtil.info(source, "voxelprint.status.min_corner",
                selection.min().getX(), selection.min().getY(), selection.min().getZ());
        ChatUtil.info(source, "voxelprint.status.max_corner",
                selection.max().getX(), selection.max().getY(), selection.max().getZ());
        ChatUtil.info(source, "voxelprint.status.size",
                selection.width(), selection.height(), selection.depth());
        ChatUtil.info(source, "voxelprint.status.volume", selection.volume());
    }

    private static void reportProblem(FabricClientCommandSource source, SelectionValidator.Result result) {
        SelectionValidator.Problem problem = result.problem().orElseThrow();
        switch (problem) {
            case NO_POSITIONS -> ChatUtil.error(source, "voxelprint.error.no_positions");
            case DIMENSION_MISMATCH -> ChatUtil.error(source, "voxelprint.error.dimension_mismatch");
            case EDGE_EXCEEDED -> ChatUtil.error(source, "voxelprint.error.edge_exceeded",
                    result.limits().maxEdge());
            case VOLUME_EXCEEDED -> ChatUtil.error(source, "voxelprint.error.volume_exceeded",
                    result.limits().maxVolume());
        }
    }

    private enum Corner {
        FIRST("voxelprint.selection.pos1_set"),
        SECOND("voxelprint.selection.pos2_set");

        private final String messageKey;

        Corner(String messageKey) {
            this.messageKey = messageKey;
        }

        String messageKey() {
            return messageKey;
        }
    }
}
