package de.tobi.voxelprint.command;

import de.iani.cubesideutils.commands.ArgsParser;
import de.tobi.voxelprint.VoxelPrint;
import de.tobi.voxelprint.format.McPrintFormat;
import de.tobi.voxelprint.upload.UploadService;
import de.tobi.voxelprint.util.ChatUtil;
import de.tobi.voxelprint.util.FileNameValidator;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;

/**
 * Sends an export to the website and hands back a link to it.
 *
 * <p>This is what the Upload button in the export message runs, and it can be
 * typed as well. The upload itself happens off the client thread; when it comes
 * back, the answer is a single clickable line that opens the build already
 * imported, so nobody has to find a file and drag it anywhere.
 *
 * <p>The link is clicked rather than opened from here on purpose: Minecraft
 * warns before following a link out of the game, and a mod quietly opening a
 * browser is exactly the behaviour that warning exists for.
 */
public final class UploadCommand extends VoxelPrintSubCommand {

    public UploadCommand(CommandContext context) {
        super(context);
    }

    @Override
    public String getUsage() {
        return "<name>";
    }

    /** The exports that are actually there, so the name can be completed. */
    @Override
    public Collection<String> onTabComplete(FabricClientCommandSource sender, String alias, ArgsParser args) {
        if (args.remaining() > 1) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (var entries = Files.list(context.files().exportDirectory())) {
            entries.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(McPrintFormat.EXTENSION))
                    .map(name -> name.substring(0, name.length() - McPrintFormat.EXTENSION.length()))
                    .sorted()
                    .forEach(names::add);
        } catch (IOException e) {
            // Nothing to complete is a fine answer; the command still works.
            VoxelPrint.LOGGER.debug("Could not list exports for tab completion", e);
        }
        return names;
    }

    @Override
    public boolean onCommand(FabricClientCommandSource sender, String alias, String commandString, ArgsParser args) {
        if (!args.hasNext()) {
            return false;
        }
        String raw = args.getNext();

        String site = context.config().webPlatformUrl();
        if (site.isEmpty()) {
            ChatUtil.error(sender, "voxelprint.error.no_web_platform");
            return true;
        }

        Optional<String> name = FileNameValidator.validate(raw);
        if (name.isEmpty()) {
            ChatUtil.error(sender, "voxelprint.error.invalid_name");
            return true;
        }

        Path file;
        try {
            file = context.files().resolveTarget(name.get());
        } catch (IOException e) {
            ChatUtil.error(sender, "voxelprint.error.export_failed");
            return true;
        }
        if (!Files.isRegularFile(file)) {
            ChatUtil.error(sender, "voxelprint.error.no_such_export", raw);
            return true;
        }

        ChatUtil.info(sender, "voxelprint.upload.started", file.getFileName().toString());

        Minecraft client = sender.getClient();
        context.uploads().upload(file, site).thenAccept(
                result -> client.execute(() -> report(client, result)));
        return true;
    }

    /** Back on the client thread, where chat may be written to. */
    private static void report(Minecraft client, UploadService.Uploaded result) {
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }
        if (!result.success()) {
            ChatUtil.error(player, "voxelprint.error.upload_failed", result.error());
            return;
        }

        ChatUtil.success(player, "voxelprint.upload.done");
        try {
            ChatUtil.actions(player, ChatUtil.action(
                    "voxelprint.action.open_project",
                    "voxelprint.action.open_project.hover",
                    new ClickEvent.OpenUrl(new URI(result.projectUrl()))));
        } catch (URISyntaxException e) {
            // The address was built from a configured base the config already
            // checked, so this means the base was odd in a way that check let
            // through; say the address rather than nothing.
            ChatUtil.info(player, "voxelprint.upload.done");
            VoxelPrint.LOGGER.warn("Uploaded project address is not a URI: {}", result.projectUrl(), e);
        }
    }
}
