package de.tobi.voxelprint.command;

import de.iani.cubesideutils.commands.ArgsParser;
import de.tobi.voxelprint.selection.SelectionState;
import de.tobi.voxelprint.selection.SelectionValidator;
import de.tobi.voxelprint.util.ChatUtil;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/** Says what is selected and whether it can be exported. */
public final class StatusCommand extends VoxelPrintSubCommand {

    public StatusCommand(CommandContext context) {
        super(context);
    }

    @Override
    public boolean onCommand(FabricClientCommandSource sender, String alias, String commandString, ArgsParser args) {
        SelectionState state = context.selections().get();
        SelectionValidator.Result result = SelectionValidator.validate(state, context.limits());

        ChatUtil.info(sender, "voxelprint.status.header");
        ChatUtil.info(sender, "voxelprint.status.dimension",
                sender.getLevel().dimension().identifier().toString());
        CommandFeedback.corner(sender, 1, state.first().orElse(null));
        CommandFeedback.corner(sender, 2, state.second().orElse(null));

        result.selection().ifPresent(selection -> CommandFeedback.measurements(sender, selection));

        if (result.isValid()) {
            ChatUtil.info(sender, "voxelprint.status.valid");
        } else {
            CommandFeedback.problem(sender, result);
        }
        return true;
    }
}
