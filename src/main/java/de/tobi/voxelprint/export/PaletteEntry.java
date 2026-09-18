package de.tobi.voxelprint.export;

import java.util.Objects;

/**
 * One distinct block state occurring in a snapshot.
 *
 * @param blockId    plain block id, for example {@code minecraft:oak_stairs}
 * @param blockState full canonical state, for example
 *                   {@code minecraft:oak_stairs[facing=north,half=bottom]}
 * @param air        whether this state is air, so statistics can skip it
 */
public record PaletteEntry(String blockId, String blockState, boolean air) {

    public PaletteEntry {
        Objects.requireNonNull(blockId, "blockId");
        Objects.requireNonNull(blockState, "blockState");
    }
}
