package de.tobi.voxelprint.format;

import de.tobi.voxelprint.export.BlockBox;
import de.tobi.voxelprint.export.ExportSnapshot;
import java.util.ArrayList;
import java.util.List;

/**
 * Contents of {@code shapes.json}: what each block state actually looks like.
 *
 * <p>Until now an export said only which block sits where. A reader outside
 * Minecraft had no way to know that a stair is not a cube, because the shape
 * lives in Minecraft's own models. This carries it along.
 *
 * <p>One entry per palette entry, in the same order, so entry {@code n} belongs
 * to palette entry {@code n}. Each is a list of axis-aligned boxes: one for a
 * full block, two for a stair, several for a fence, none for air. Coordinates
 * are block-local, from 0 to 1, so a shape is independent of where the block
 * sits and can be reused for every block of that state.
 *
 * <pre>{@code
 * {
 *   "formatVersion": 1,
 *   "coordinates": "block-local, 0 to 1, indexed like the structure palette",
 *   "shapes": [
 *     [],
 *     [[0, 0, 0, 1, 1, 1]],
 *     [[0, 0, 0, 1, 0.5, 1], [0, 0.5, 0.5, 1, 1, 1]]
 *   ]
 * }
 * }</pre>
 */
public record BlockShapes(int formatVersion, String coordinates, List<List<double[]>> shapes) {

    private static final int FORMAT_VERSION = 1;

    private static final String COORDINATES = "block-local, 0 to 1, indexed like the structure palette";

    public static BlockShapes of(ExportSnapshot snapshot) {
        List<List<double[]>> shapes = new ArrayList<>(snapshot.shapes().size());
        for (List<BlockBox> boxes : snapshot.shapes()) {
            List<double[]> entry = new ArrayList<>(boxes.size());
            for (BlockBox box : boxes) {
                entry.add(new double[] {
                        box.minX(), box.minY(), box.minZ(),
                        box.maxX(), box.maxY(), box.maxZ(),
                });
            }
            shapes.add(entry);
        }
        return new BlockShapes(FORMAT_VERSION, COORDINATES, shapes);
    }

    /**
     * How many entries are something other than a plain cube.
     *
     * <p>Only used for logging, but it is the number that says whether carrying
     * shapes was worth the bytes for this particular export.
     */
    public static int detailedCount(ExportSnapshot snapshot) {
        int detailed = 0;
        for (List<BlockBox> boxes : snapshot.shapes()) {
            if (boxes.isEmpty()) {
                continue;
            }
            if (boxes.size() > 1 || !boxes.get(0).isFullBlock()) {
                detailed++;
            }
        }
        return detailed;
    }
}
