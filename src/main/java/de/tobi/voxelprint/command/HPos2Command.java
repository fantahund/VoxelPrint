package de.tobi.voxelprint.command;

/** Sets the second corner to the block the player is looking at. */
public final class HPos2Command extends SelectionCornerCommand {

    public HPos2Command(CommandContext context) {
        super(context, false, true);
    }
}
