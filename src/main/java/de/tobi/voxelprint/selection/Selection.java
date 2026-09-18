package de.tobi.voxelprint.selection;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * An axis-aligned, immutable cuboid selection within a single dimension.
 *
 * <p>Both corners are inclusive, so a selection whose corners are equal still
 * contains exactly one block.
 */
public record Selection(ResourceKey<Level> dimension, BlockPos min, BlockPos max) {

    public Selection {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        if (min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()) {
            throw new IllegalArgumentException("min must not exceed max: " + min + " / " + max);
        }
        min = min.immutable();
        max = max.immutable();
    }

    /** Builds the cuboid spanned by two arbitrary corners. */
    public static Selection between(ResourceKey<Level> dimension, BlockPos a, BlockPos b) {
        BlockPos min = new BlockPos(
                Math.min(a.getX(), b.getX()),
                Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(
                Math.max(a.getX(), b.getX()),
                Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ()));
        return new Selection(dimension, min, max);
    }

    public int width() {
        return max.getX() - min.getX() + 1;
    }

    public int height() {
        return max.getY() - min.getY() + 1;
    }

    public int depth() {
        return max.getZ() - min.getZ() + 1;
    }

    /**
     * Total number of blocks, air included.
     *
     * <p>{@code long} on purpose: three {@code int} edges multiply well past
     * {@link Integer#MAX_VALUE} long before any limit would stop them.
     */
    public long volume() {
        return (long) width() * height() * depth();
    }

    /** Length of the longest of the three edges. */
    public int longestEdge() {
        return Math.max(width(), Math.max(height(), depth()));
    }

    /** Returns the dimension id, for example {@code minecraft:overworld}. */
    public String dimensionId() {
        return dimension.identifier().toString();
    }
}
