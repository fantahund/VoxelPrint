package de.tobi.voxelprint.export;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * An immutable copy of everything an export needs.
 *
 * <p>This is the hand-over point between the game and the background worker.
 * It holds no Minecraft types at all, which is what makes it safe to touch from
 * another thread: once built, nothing in here can change and nothing in here can
 * reach back into the world.
 *
 * <p>Blocks are stored as palette indices in the same order the Sponge schematic
 * format uses, {@code x + z * width + y * width * depth}. Writing the internal
 * format and, later, a real {@code .schem} therefore needs no repacking.
 */
public final class ExportSnapshot {

    private final String dimensionId;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final int width;
    private final int height;
    private final int depth;
    private final List<PaletteEntry> palette;
    private final List<List<BlockBox>> shapes;
    private final List<List<ModelQuad>> models;
    private final List<MaterialRegion> materials;
    private final int[] blocks;
    private final long[] counts;
    private final long nonAirBlockCount;
    private final String minecraftVersion;
    private final int dataVersion;
    private final Instant capturedAt;

    ExportSnapshot(String dimensionId,
                   int originX, int originY, int originZ,
                   int width, int height, int depth,
                   List<PaletteEntry> palette,
                   List<List<BlockBox>> shapes,
                   List<List<ModelQuad>> models,
                   List<MaterialRegion> materials,
                   int[] blocks,
                   long[] counts,
                   long nonAirBlockCount,
                   String minecraftVersion,
                   int dataVersion,
                   Instant capturedAt) {
        this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.palette = List.copyOf(palette);
        this.shapes = shapes.stream().map(List::copyOf).toList();
        this.models = models.stream().map(List::copyOf).toList();
        this.materials = List.copyOf(materials);
        // Defensive copies: the arrays must not stay reachable from the caller,
        // or "immutable" would be a promise this class cannot keep.
        this.blocks = blocks.clone();
        this.counts = counts.clone();
        this.nonAirBlockCount = nonAirBlockCount;
        this.minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        this.dataVersion = dataVersion;
        this.capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
    }

    public String dimensionId() {
        return dimensionId;
    }

    public int originX() {
        return originX;
    }

    public int originY() {
        return originY;
    }

    public int originZ() {
        return originZ;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int depth() {
        return depth;
    }

    /** All distinct block states, indexed exactly as {@link #paletteIndexAt}. */
    public List<PaletteEntry> palette() {
        return palette;
    }

    /**
     * The solid shape of each palette entry, in the same order as the palette.
     *
     * <p>A list of boxes per entry: one for a full block, two for a stair, none
     * for air. Empty overall when shapes were not exported.
     */
    public List<List<BlockBox>> shapes() {
        return shapes;
    }

    /**
     * The real model of each palette entry, in the palette's order.
     *
     * <p>Empty for an entry whose model could not be read, and empty overall
     * when models were not exported. A reader falls back to {@link #shapes()}.
     */
    public List<List<ModelQuad>> models() {
        return models;
    }

    /** Every material the models refer to, indexed by {@link ModelQuad#material()}. */
    public List<MaterialRegion> materials() {
        return materials;
    }

    /** Number of blocks, air included. */
    public int volume() {
        return blocks.length;
    }

    /** Number of blocks that are not air. */
    public long nonAirBlockCount() {
        return nonAirBlockCount;
    }

    /** How often the given palette entry occurs. */
    public long count(int paletteIndex) {
        return counts[paletteIndex];
    }

    /** Palette index at a linear position, see the class documentation. */
    public int paletteIndexAt(int linearIndex) {
        return blocks[linearIndex];
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }

    public int dataVersion() {
        return dataVersion;
    }

    public Instant capturedAt() {
        return capturedAt;
    }
}
