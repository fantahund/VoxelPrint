package de.tobi.voxelprint.command;

import de.tobi.voxelprint.config.VoxelPrintConfig;
import de.tobi.voxelprint.export.ExportFileManager;
import de.tobi.voxelprint.export.ExportService;
import de.tobi.voxelprint.selection.SelectionManager;
import de.tobi.voxelprint.selection.SelectionValidator;
import de.tobi.voxelprint.upload.UploadService;

/**
 * Everything the subcommands are allowed to touch.
 *
 * <p>Handed to each of them at registration rather than reached for through a
 * static: a subcommand that names its dependencies can be read on its own, and
 * the list here is the whole surface the command layer has on the rest of the
 * mod.
 *
 * @param selections       the two corners
 * @param config           settings, read live so a change applies at once
 * @param exports          starts an export and reports when it finishes
 * @param files            resolves the export directory and file names
 * @param uploads          sends a finished export to the website
 * @param openConfigScreen asks for the settings screen on the next tick
 */
public record CommandContext(SelectionManager selections,
                             VoxelPrintConfig config,
                             ExportService exports,
                             ExportFileManager files,
                             UploadService uploads,
                             Runnable openConfigScreen) {

    /** The current selection limits, which the settings screen can change. */
    public SelectionValidator.Limits limits() {
        return new SelectionValidator.Limits(config.maxSelectionVolume(), config.maxSelectionEdge());
    }
}
