package de.tobi.voxelprint.command;

/** Sets the first corner to the block the player is looking at. */
public final class HPos1Command extends SelectionCornerCommand {

    public HPos1Command(CommandContext context) {
        super(context, true, true);
    }
}
