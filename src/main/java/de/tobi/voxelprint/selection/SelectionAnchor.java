package de.tobi.voxelprint.selection;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * One corner of a selection, together with the dimension it was set in.
 *
 * <p>The dimension travels with the position because a player can set the two
 * corners in different dimensions, which has to be detected and reported rather
 * than silently producing a nonsensical cuboid.
 */
public record SelectionAnchor(ResourceKey<Level> dimension, BlockPos position) {

    public SelectionAnchor {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(position, "position");
        // Entity positions may be handed out as mutable instances; copying here
        // keeps this record genuinely immutable and safe to hand to other threads.
        position = position.immutable();
    }

    /** Returns the dimension id, for example {@code minecraft:overworld}. */
    public String dimensionId() {
        return dimension.identifier().toString();
    }
}
