package de.tobi.voxelprint.command;

/** Sets the first corner to the block the player is standing on. */
public final class Pos1Command extends SelectionCornerCommand {

    public Pos1Command(CommandContext context) {
        super(context, true, false);
    }
}
