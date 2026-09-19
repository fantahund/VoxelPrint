package de.tobi.voxelprint.command;

import com.mojang.brigadier.CommandDispatcher;
import de.iani.cubesideutils.commands.ArgsParser;
import de.iani.cubesideutils.fabric.commands.CommandRouter;
import de.iani.cubesideutils.fabric.commands.CommandUtil;
import de.tobi.voxelprint.config.VoxelPrintConfig;
import de.tobi.voxelprint.export.ExportFileManager;
import de.tobi.voxelprint.export.ExportService;
import de.tobi.voxelprint.selection.SelectionManager;
import de.tobi.voxelprint.upload.UploadService;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * Wires the subcommands to {@code /voxelprint} and {@code /vp}.
 *
 * <p>Registration and nothing else. Every subcommand is its own class, so what
 * one of them does is read in the file named after it rather than found in the
 * middle of a long chain of anonymous classes -- which is what this was, and
 * which made the whole command surface one file nobody wanted to open.
 */
public final class VoxelPrintCommands {

    private static final String ROOT = "voxelprint";
    private static final String ALIAS = "vp";

    /** What the root offers, in the order somebody works through them. */
    private static final List<String> SUBCOMMANDS =
            List.of("pos1", "pos2", "hpos1", "hpos2", "status", "deselect", "export", "upload", "config");

    private final CommandContext context;

    public VoxelPrintCommands(SelectionManager selections,
                              VoxelPrintConfig config,
                              ExportService exports,
                              ExportFileManager files,
                              UploadService uploads,
                              Runnable openConfigScreen) {
        this.context = new CommandContext(
                Objects.requireNonNull(selections, "selections"),
                Objects.requireNonNull(config, "config"),
                Objects.requireNonNull(exports, "exports"),
                Objects.requireNonNull(files, "files"),
                Objects.requireNonNull(uploads, "uploads"),
                Objects.requireNonNull(openConfigScreen, "openConfigScreen"));
    }

    public void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        CommandRouter router = new CommandRouter();

        router.addCommandMapping(new RootCommand(context));
        router.addCommandMapping(new Pos1Command(context), "pos1");
        router.addCommandMapping(new Pos2Command(context), "pos2");
        router.addCommandMapping(new HPos1Command(context), "hpos1");
        router.addCommandMapping(new HPos2Command(context), "hpos2");
        router.addCommandMapping(new StatusCommand(context), "status");
        router.addCommandMapping(new DeselectCommand(context), "deselect");
        router.addCommandMapping(new ExportCommand(context), "export");
        router.addCommandMapping(new UploadCommand(context), "upload");
        router.addCommandMapping(new ConfigCommand(context), "config");

        CommandUtil.registerCommand(dispatcher, ROOT, router);
        CommandUtil.registerCommand(dispatcher, ALIAS, router);
    }

    /**
     * The bare {@code /vp}, which lists what there is.
     *
     * <p>It also supplies the first level of tab completion: the router does not
     * offer the subcommand names on its own, and a command whose options have to
     * be known in advance is a command nobody finds.
     */
    private static final class RootCommand extends VoxelPrintSubCommand {

        RootCommand(CommandContext context) {
            super(context);
        }

        @Override
        public boolean onCommand(FabricClientCommandSource sender, String alias, String commandString, ArgsParser args) {
            // False asks the router for its help output, which lists the
            // subcommands with their usage.
            return false;
        }

        @Override
        public Collection<String> onTabComplete(FabricClientCommandSource sender, String alias, ArgsParser args) {
            return args.remaining() <= 1 ? SUBCOMMANDS : List.of();
        }
    }
}
