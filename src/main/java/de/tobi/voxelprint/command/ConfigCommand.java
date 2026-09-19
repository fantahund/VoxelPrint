package de.tobi.voxelprint.command;

import de.iani.cubesideutils.commands.ArgsParser;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * Opens the settings screen.
 *
 * <p>Asks for it rather than opening it: a screen cannot be opened from inside
 * command handling, so the request is picked up on the next client tick.
 */
public final class ConfigCommand extends VoxelPrintSubCommand {

    public ConfigCommand(CommandContext context) {
        super(context);
    }

    @Override
    public boolean onCommand(FabricClientCommandSource sender, String alias, String commandString, ArgsParser args) {
        context.openConfigScreen().run();
        return true;
    }
}
