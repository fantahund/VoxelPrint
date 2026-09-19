package de.tobi.voxelprint.command;

import de.iani.cubesideutils.commands.ArgsParser;
import de.tobi.voxelprint.selection.SelectionAnchor;
import de.tobi.voxelprint.util.ChatUtil;
import java.util.Optional;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Setting one of the two corners, wherever the block comes from.
 *
 * <p>Four commands differ in two bits each -- which corner, and whether the
 * block is the one under your feet or the one you are looking at -- so those two
 * bits are what the four subclasses supply and nothing else is written twice.
 */
abstract class SelectionCornerCommand extends VoxelPrintSubCommand {

    /** As far as a block can be picked, matching the vanilla reach by a margin. */
    private static final double MAX_TARGET_DISTANCE = 128.0;

    private final boolean first;
    private final boolean looking;

    SelectionCornerCommand(CommandContext context, boolean first, boolean looking) {
        super(context);
        this.first = first;
        this.looking = looking;
    }

    @Override
    public boolean onCommand(FabricClientCommandSource sender, String alias, String commandString, ArgsParser args) {
        LocalPlayer player = sender.getPlayer();
        Optional<BlockPos> position = looking ? targeted(player) : Optional.of(player.blockPosition());

        if (position.isEmpty()) {
            ChatUtil.error(sender, "voxelprint.error.no_target_block", (int) MAX_TARGET_DISTANCE);
            return true;
        }

        SelectionAnchor anchor = new SelectionAnchor(player.level().dimension(), position.get());
        if (first) {
            context.selections().setFirst(anchor);
        } else {
            context.selections().setSecond(anchor);
        }

        context.config().debug("Corner {} set to {} in {}",
                first ? 1 : 2, anchor.position(), anchor.dimensionId());
        ChatUtil.success(sender, first ? "voxelprint.selection.pos1_set" : "voxelprint.selection.pos2_set",
                anchor.position().getX(),
                anchor.position().getY(),
                anchor.position().getZ(),
                anchor.dimensionId());
        return true;
    }

    /** The block the player is looking at, if it is a block and within reach. */
    private static Optional<BlockPos> targeted(LocalPlayer player) {
        HitResult hit = player.pick(MAX_TARGET_DISTANCE, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        return Optional.of(((BlockHitResult) hit).getBlockPos().immutable());
    }
}
