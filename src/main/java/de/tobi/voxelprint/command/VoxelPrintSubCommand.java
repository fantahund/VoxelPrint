package de.tobi.voxelprint.command;

import de.iani.cubesideutils.commands.ArgsParser;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * What every VoxelPrint subcommand has in common.
 *
 * <p>The context, and a tab completion that offers nothing. That second part is
 * the point: left to the default, a subcommand that takes no argument still
 * invites one, and {@code /vp deselect <tab>} offering names is how somebody
 * ends up believing a selection has a name. A subcommand that does take an
 * argument says so by overriding this.
 */
public abstract class VoxelPrintSubCommand extends SubCommandBase {

    protected final CommandContext context;

    protected VoxelPrintSubCommand(CommandContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    @Override
    public Collection<String> onTabComplete(FabricClientCommandSource sender, String alias, ArgsParser args) {
        return List.of();
    }
}
