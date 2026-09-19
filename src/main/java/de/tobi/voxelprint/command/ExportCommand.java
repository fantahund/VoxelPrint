package de.tobi.voxelprint.command;

import de.iani.cubesideutils.commands.ArgsParser;
import de.tobi.voxelprint.VoxelPrint;
import de.tobi.voxelprint.export.ExportResult;
import de.tobi.voxelprint.selection.SelectionState;
import de.tobi.voxelprint.selection.SelectionValidator;
import de.tobi.voxelprint.util.ChatUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;

/**
 * Writes the selection to a {@code .mcprint} file.
 *
 * <p>When it lands, the message that says so carries the next step with it: the
 * folder it was written to, and -- when a website is configured -- an upload
 * that puts the build straight into the browser. Telling somebody a file exists
 * and leaving them to find it is half an answer.
 */
public final class ExportCommand extends VoxelPrintSubCommand {

    public ExportCommand(CommandContext context) {
        super(context);
    }

    @Override
    public String getUsage() {
        return "<name>";
    }

    @Override
    public Collection<String> onTabComplete(FabricClientCommandSource sender, String alias, ArgsParser args) {
        // A name that does not exist yet cannot be suggested, so this offers
        // nothing rather than something misleading.
        return List.of();
    }

    @Override
    public boolean onCommand(FabricClientCommandSource sender, String alias, String commandString, ArgsParser args) {
        if (!args.hasNext()) {
            return false;
        }
        String name = args.getNext();

        SelectionState state = context.selections().get();
        SelectionValidator.Result validation = SelectionValidator.validate(state, context.limits());
        if (!validation.isValid()) {
            CommandFeedback.problem(sender, validation);
            return true;
        }

        if (context.config().showExportNotifications()) {
            ChatUtil.info(sender, "voxelprint.export.preparing");
        }

        Minecraft client = sender.getClient();
        ClientLevel level = sender.getLevel();
        ExportResult accepted = context.exports().start(
                client, level, validation.selection().orElseThrow(), name, finished(client));

        if (accepted.status() != ExportResult.Status.ACCEPTED) {
            CommandFeedback.refusedExport(sender, accepted.status());
            return true;
        }

        if (context.config().showExportNotifications()) {
            ChatUtil.info(sender, "voxelprint.export.snapshot_created");
        }
        return true;
    }

    /** What to say once the file is on disk, which happens off this thread. */
    private Consumer<ExportResult> finished(Minecraft client) {
        return result -> {
            LocalPlayer player = client.player;
            if (player == null) {
                return;
            }
            switch (result.status()) {
                case SUCCESS -> {
                    if (context.config().showExportNotifications()) {
                        ChatUtil.success(player, "voxelprint.export.success", result.fileName());
                    }
                    offerNextStep(player, result);
                }
                case FILE_EXISTS -> ChatUtil.error(player, "voxelprint.error.file_exists");
                default -> ChatUtil.error(player, "voxelprint.error.export_failed");
            }
        };
    }

    /**
     * The row of things to do with the file that was just written.
     *
     * <p>The folder is always offered. The upload is offered only when there is
     * somewhere to send it, because a button that cannot work is worse than no
     * button.
     */
    private void offerNextStep(LocalPlayer player, ExportResult result) {
        Path directory;
        try {
            directory = context.files().exportDirectory();
        } catch (IOException e) {
            // The file was written, so the directory exists; if it cannot be
            // named now, the message is still worth sending without the button.
            VoxelPrint.LOGGER.warn("Could not resolve the export directory for the chat link", e);
            return;
        }

        String name = stripExtension(result.fileName());
        String site = context.config().webPlatformUrl();

        if (site.isEmpty()) {
            ChatUtil.actions(player, ChatUtil.action(
                    "voxelprint.action.open_folder",
                    "voxelprint.action.open_folder.hover",
                    new ClickEvent.OpenFile(directory)));
            return;
        }

        ChatUtil.actions(player,
                ChatUtil.action("voxelprint.action.open_folder",
                        "voxelprint.action.open_folder.hover",
                        new ClickEvent.OpenFile(directory)),
                ChatUtil.action("voxelprint.action.upload",
                        "voxelprint.action.upload.hover",
                        new ClickEvent.RunCommand("/voxelprint upload " + name)));
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }
}
