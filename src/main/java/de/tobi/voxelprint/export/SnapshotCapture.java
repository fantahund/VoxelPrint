package de.tobi.voxelprint.export;

import de.tobi.voxelprint.VoxelPrint;
import de.tobi.voxelprint.selection.Selection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Reads block states out of the world and freezes them into an
 * {@link ExportSnapshot}.
 *
 * <p>This is the only class in the export packages that touches Minecraft at
 * all. Everything downstream -- the manifest, the block summary, the structure
 * writers, the archive -- works purely on the snapshot. That is what allows the
 * actual file writing to happen on a background thread without ever reaching
 * into the world.
 *
 * <p>Must be called on the client thread.
 */
public final class SnapshotCapture {

    private SnapshotCapture() {
        throw new AssertionError("No instances.");
    }

    /**
     * True when every chunk the selection touches is currently loaded.
     *
     * <p>Walks the chunk grid and asks {@code hasChunk} directly. The convenient
     * {@code hasChunksAt} overloads are all deprecated in 26.3.
     *
     * <p>This matters more on a client than it would on a server: the client
     * only holds what is within render distance, and an unloaded chunk reads as
     * air. Without this check an export would quietly contain holes.
     */
    public static boolean isFullyLoaded(ClientLevel level, Selection selection) {
        // Arithmetic shift, so this stays correct for negative coordinates.
        int minChunkX = selection.min().getX() >> 4;
        int maxChunkX = selection.max().getX() >> 4;
        int minChunkZ = selection.min().getZ() >> 4;
        int maxChunkZ = selection.max().getZ() >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Copies the selection out of the world.
     *
     * <p>Runs in one go on the client thread. That is a deliberate trade: the
     * selection limits keep the pause to a single noticeable frame, and in
     * exchange the result is guaranteed to be one consistent moment in time
     * rather than a mixture of several.
     */
    public static ExportSnapshot capture(ClientLevel level, Selection selection) {
        return capture(level, selection, true, null, 1);
    }

    /**
     * Copies the selection out of the world.
     *
     * @param withShapes whether to record the solid shape of each block state
     * @param models     reads the real model of each state, or null to skip it
     * @param detail     how finely a face may follow its texture, see
     *                   {@link QuadSplitter}
     */
    public static ExportSnapshot capture(ClientLevel level, Selection selection,
                                         boolean withShapes, Minecraft models, int detail) {
        int width = selection.width();
        int height = selection.height();
        int depth = selection.depth();
        long volume = selection.volume();
        if (volume > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Selection too large to snapshot: " + volume);
        }

        int[] blocks = new int[(int) volume];
        Map<BlockState, Integer> indices = new HashMap<>();
        List<PaletteEntry> palette = new ArrayList<>();
        List<List<BlockBox>> shapes = new ArrayList<>();
        List<List<ModelQuad>> quads = new ArrayList<>();
        ModelCapture modelCapture = models == null ? null : new ModelCapture(models, detail);
        List<long[]> counters = new ArrayList<>();
        long nonAir = 0L;

        int minX = selection.min().getX();
        int minY = selection.min().getY();
        int minZ = selection.min().getZ();

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = 0; y < height; y++) {
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    cursor.set(minX + x, minY + y, minZ + z);
                    BlockState state = level.getBlockState(cursor);

                    Integer known = indices.get(state);
                    int index;
                    if (known == null) {
                        index = palette.size();
                        indices.put(state, index);
                        palette.add(describe(state));
                        // Read at the position where the state was first seen.
                        // Fences and walls derive their shape from neighbours,
                        // but those connections are already part of the block
                        // state, so the shape is the same wherever that exact
                        // state occurs.
                        shapes.add(withShapes ? shapeOf(level, state, cursor) : List.of());
                        quads.add(modelCapture == null
                                ? List.of()
                                : modelCapture.quadsOf(state, level, cursor));
                        counters.add(new long[1]);
                    } else {
                        index = known;
                    }

                    counters.get(index)[0]++;
                    if (!state.isAir()) {
                        nonAir++;
                    }
                    blocks[x + z * width + y * width * depth] = index;
                }
            }
        }

        long[] counts = new long[counters.size()];
        for (int i = 0; i < counts.length; i++) {
            counts[i] = counters.get(i)[0];
        }

        return new ExportSnapshot(
                selection.dimensionId(),
                minX, minY, minZ,
                width, height, depth,
                palette,
                shapes,
                quads,
                modelCapture == null ? List.of() : modelCapture.materials(),
                blocks,
                counts,
                nonAir,
                SharedConstants.getCurrentVersion().name(),
                SharedConstants.getCurrentVersion().dataVersion().version(),
                Instant.now());
    }

    /**
     * Reads the solid shape of a block as a list of boxes.
     *
     * <p>The outline shape rather than the collision shape: a torch, a plant or
     * a carpet has no collision at all but is very much there to print.
     *
     * <p>Falls back to the full block when a shape is empty but the block is
     * not air. That happens for blocks Minecraft draws without giving them an
     * outline, and a printed model with holes where those blocks were would be
     * worse than one that is slightly too generous.
     */
    private static List<BlockBox> shapeOf(ClientLevel level, BlockState state, BlockPos position) {
        if (state.isAir()) {
            return List.of();
        }

        VoxelShape shape;
        try {
            shape = state.getShape(level, position);
        } catch (RuntimeException e) {
            // A modded block may not survive being asked outside its own
            // context. One awkward block must not fail the whole export.
            VoxelPrint.LOGGER.warn("Could not read the shape of {}, using a full block", state, e);
            return List.of(BlockBox.FULL);
        }

        if (shape.isEmpty()) {
            return List.of(BlockBox.FULL);
        }

        List<BlockBox> boxes = new ArrayList<>();
        for (AABB box : shape.toAabbs()) {
            boxes.add(BlockBox.rounded(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
        }
        return boxes;
    }

    private static PaletteEntry describe(BlockState state) {
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return new PaletteEntry(blockId, serialize(state, blockId), state.isAir());
    }

    /**
     * Renders a block state as {@code namespace:id[property=value,...]}.
     *
     * <p>Properties are sorted by name by us rather than taken in Minecraft's
     * own order. That order is an implementation detail which may change between
     * versions, and a stable order is what makes two exports of the same build
     * comparable.
     */
    private static String serialize(BlockState state, String blockId) {
        String properties = state.getValues()
                .sorted(Comparator.comparing(value -> value.property().getName()))
                .map(value -> value.property().getName() + "=" + value.valueName())
                .collect(Collectors.joining(","));
        return properties.isEmpty() ? blockId : blockId + "[" + properties + "]";
    }
}
