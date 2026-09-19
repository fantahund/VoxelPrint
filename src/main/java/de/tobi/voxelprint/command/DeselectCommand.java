package de.tobi.voxelprint.command;

import de.iani.cubesideutils.commands.ArgsParser;
import de.tobi.voxelprint.util.ChatUtil;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * Drops the selection.
 *
 * <p>There used to be a {@code clear} beside this doing exactly the same thing,
 * and offering two words for one action only makes somebody wonder what the
 * difference is. There is one word now, and it takes no argument: this runs on
 * the client, where there is one selection and it has no name.
 */
public final class DeselectCommand extends VoxelPrintSubCommand {

    public DeselectCommand(CommandContext context) {
        super(context);
    }

    @Override
    public boolean onCommand(FabricClientCommandSource sender, String alias, String commandString, ArgsParser args) {
        if (context.selections().clear()) {
            ChatUtil.success(sender, "voxelprint.selection.cleared");
        } else {
            ChatUtil.info(sender, "voxelprint.selection.nothing_to_clear");
        }
        return true;
    }
}
