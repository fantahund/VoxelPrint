package de.tobi.voxelprint.command;

import de.tobi.voxelprint.export.ExportResult;
import de.tobi.voxelprint.selection.Selection;
import de.tobi.voxelprint.selection.SelectionAnchor;
import de.tobi.voxelprint.selection.SelectionValidator;
import de.tobi.voxelprint.util.ChatUtil;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * The wording shared between subcommands.
 *
 * <p>Several of them have to explain the same things -- why a selection is not
 * usable, what it measures, why an export was refused -- and an explanation
 * that differs depending on which command you asked is worse than one that does
 * not.
 */
final class CommandFeedback {

    private CommandFeedback() {
        throw new AssertionError("No instances.");
    }

    static void corner(FabricClientCommandSource source, int index, SelectionAnchor anchor) {
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

    static void measurements(FabricClientCommandSource source, Selection selection) {
        ChatUtil.info(source, "voxelprint.status.min_corner",
                selection.min().getX(), selection.min().getY(), selection.min().getZ());
        ChatUtil.info(source, "voxelprint.status.max_corner",
                selection.max().getX(), selection.max().getY(), selection.max().getZ());
        ChatUtil.info(source, "voxelprint.status.size",
                selection.width(), selection.height(), selection.depth());
        ChatUtil.info(source, "voxelprint.status.volume", selection.volume());
    }

    /** Why the selection cannot be exported, in terms of what to do about it. */
    static void problem(FabricClientCommandSource source, SelectionValidator.Result result) {
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

    /** Why an export was turned down before anything was written. */
    static void refusedExport(FabricClientCommandSource source, ExportResult.Status status) {
        switch (status) {
            case INVALID_NAME -> ChatUtil.error(source, "voxelprint.error.invalid_name");
            case ALREADY_RUNNING -> ChatUtil.error(source, "voxelprint.error.export_running");
            case CHUNKS_NOT_LOADED -> ChatUtil.error(source, "voxelprint.error.chunks_not_loaded");
            case FILE_EXISTS -> ChatUtil.error(source, "voxelprint.error.file_exists");
            default -> ChatUtil.error(source, "voxelprint.error.export_failed");
        }
    }
}
