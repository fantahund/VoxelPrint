package de.tobi.voxelprint.format;

import de.tobi.voxelprint.export.ExportSnapshot;
import de.tobi.voxelprint.export.MaterialRegion;
import de.tobi.voxelprint.export.ModelQuad;
import java.util.ArrayList;
import java.util.List;

/**
 * Contents of {@code models.json}: the real model of each block state.
 *
 * <p>Where {@code shapes.json} says how much space a block takes, this says
 * what it looks like. A torch is a stick with a flame rather than a small box,
 * and a piston has separate faces for its wooden casing, its body and its dark
 * front -- which is exactly what a printer with several filaments needs.
 *
 * <p>One entry per palette entry, in the palette's order, so entry {@code n}
 * belongs to palette entry {@code n}. Coordinates are block-local, so a model
 * is reused by every block of that state and the file grows with the palette
 * rather than with the number of blocks.
 *
 * <p>Materials are listed once for the whole export and referred to by index.
 * Each carries a texture name and the average colour of that texture -- never
 * the texture itself.
 *
 * <p>Grass and leaves ship a grey texture and are tinted by the biome when they
 * are drawn. The tint is already in {@code colour}; it is listed separately as
 * well so a reader can tell a green that came from the biome from one that was
 * painted into the texture.
 *
 * <pre>{@code
 * {
 *   "formatVersion": 1,
 *   "materials": [
 *     { "texture": "minecraft:block/oak_planks", "colour": "#b08a4e" },
 *     { "texture": "minecraft:block/short_grass", "tint": "#91bd59", "colour": "#54703d" }
 *   ],
 *   "models": [
 *     [],
 *     [ { "material": 0, "direction": "up", "vertices": [0,1,0, 0,1,1, 1,1,1, 1,1,0] } ]
 *   ]
 * }
 * }</pre>
 */
public record BlockModels(int formatVersion, List<Material> materials, List<List<Quad>> models) {

    private static final int FORMAT_VERSION = 1;

    /**
     * A material as written to the file.
     *
     * @param tint   the biome tint folded into the colour, left out where the
     *               texture is drawn as it is
     * @param colour the colour to print, tint included
     */
    public record Material(String texture, String tint, String colour) {
    }

    /**
     * A face as written to the file.
     *
     * @param direction the face it belongs to, left out when the model ties it
     *                  to none
     */
    public record Quad(int material, String direction, List<Double> vertices) {
    }

    public static BlockModels of(ExportSnapshot snapshot) {
        List<Material> materials = new ArrayList<>(snapshot.materials().size());
        for (MaterialRegion region : snapshot.materials()) {
            String tint = region.tint() == MaterialRegion.NO_TINT ? null : hex(region.tint());
            materials.add(new Material(region.texture(), tint, hex(region.colour())));
        }

        List<List<Quad>> models = new ArrayList<>(snapshot.models().size());
        for (List<ModelQuad> quads : snapshot.models()) {
            List<Quad> entry = new ArrayList<>(quads.size());
            for (ModelQuad quad : quads) {
                entry.add(new Quad(quad.material(), quad.direction(), rounded(quad.vertices())));
            }
            models.add(entry);
        }

        return new BlockModels(FORMAT_VERSION, materials, models);
    }

    /**
     * Rounds coordinates to five decimals.
     *
     * <p>Model coordinates arrive as floats and print with a long tail of noise.
     * Five decimals is far finer than a printer can resolve and keeps the file
     * from doubling in size for digits nobody can use.
     */
    private static List<Double> rounded(float[] vertices) {
        List<Double> values = new ArrayList<>(vertices.length);
        for (float value : vertices) {
            values.add(Math.round(value * 100_000.0) / 100_000.0);
        }
        return values;
    }

    private static String hex(int colour) {
        return String.format("#%06x", colour & 0xffffff);
    }

    /** How many quads the whole export carries. Used for logging. */
    public static int quadCount(ExportSnapshot snapshot) {
        int total = 0;
        for (List<ModelQuad> quads : snapshot.models()) {
            total += quads.size();
        }
        return total;
    }
}
