package de.tobi.voxelprint.command;

/** Sets the second corner to the block the player is standing on. */
public final class Pos2Command extends SelectionCornerCommand {

    public Pos2Command(CommandContext context) {
        super(context, false, false);
    }
}
