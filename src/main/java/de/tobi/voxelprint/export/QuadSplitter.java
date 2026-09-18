package de.tobi.voxelprint.export;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuts a face into pieces that each print in one colour.
 *
 * <p>A face carries one texture, and a texture is rarely one colour. A torch is
 * a wooden stick with a lit head; averaged as a whole it is brown, and the
 * head, which is the part anyone looks at, is gone. The same goes for a door
 * with a dark handle, a furnace with a black front and a melon with a pale rind.
 *
 * <p>So the face is laid out on a grid, each cell is given the average colour
 * of the piece of texture it shows, and neighbouring cells of the same colour
 * are joined back together. A plain face comes out as the one face it was; a
 * torch comes out as a stick and a head.
 *
 * <p>Cells the texture shows nothing at are dropped rather than coloured. That
 * shrinks a face to what is actually drawn on it, which is both more honest and
 * less to print.
 */
public final class QuadSplitter {

    /**
     * How different two colours may be and still count as one.
     *
     * <p>Measured per channel, out of 255. Generous on purpose: a stone texture
     * is noisy without being two colours, and splitting it into a dozen greys
     * would cost a lot of faces for something nobody can see. A real change of
     * material -- wood to flame, stone to iron -- is far larger than this.
     */
    private static final int SAME_COLOUR = 20;

    /**
     * The fewest texture pixels a piece of a face may be averaged from.
     *
     * <p>Below this there is nothing left to average and a cell is reading raw
     * pixels, which for stone or wood is noise rather than detail. Measured
     * rather than guessed: over whole textures, four pixels to a side separates
     * the ones that really are several colours -- glass, an end rod, a torch --
     * from the ones that are one noisy colour, with nothing in between. Take
     * cells down to a pixel or two and that gap closes completely, and
     * cobblestone starts looking as varied as a torch.
     *
     * <p>It is a count of pixels, not a fraction, so a texture pack with more
     * of them gets more detail out of the same setting.
     */
    private static final int SMALLEST_CELL = 4;

    private QuadSplitter() {
        throw new AssertionError("No instances.");
    }

    /**
     * One piece of a face: where it is, and what colour it prints in.
     *
     * @param vertices four corners of three coordinates, wound as they were
     */
    public record Patch(float[] vertices, int colour) {
    }

    /**
     * Splits one face.
     *
     * @param positions the face's four corners, three coordinates each
     * @param texels    the same four corners in texture pixels, two each
     * @param texture   the texture the face shows, or null when it could not be
     *                  read -- the face is then kept whole and left to the
     *                  caller to colour
     * @param detail    the most cells to cut each axis into; 1 keeps every face
     *                  whole and gives it a single colour
     */
    public static List<Patch> split(float[] positions, float[] texels, BlockTexture texture, int detail) {
        if (texture == null || detail < 1) {
            return List.of(new Patch(positions.clone(), BlockTexture.TRANSPARENT));
        }

        int columns = cells(texels, 0, 2, detail);
        int rows = cells(texels, 0, 6, detail);

        int[][] colours = new int[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                colours[row][column] = colourOf(texels, texture, column, columns, row, rows);
            }
        }

        List<Patch> patches = new ArrayList<>();
        for (Block block : merge(colours, rows, columns)) {
            if (block.colour == BlockTexture.TRANSPARENT) {
                // The texture shows nothing here, so neither does the print.
                continue;
            }
            patches.add(new Patch(
                    cornersOf(positions,
                            (float) block.column0 / columns, (float) (block.column1 + 1) / columns,
                            (float) block.row0 / rows, (float) (block.row1 + 1) / rows),
                    block.colour));
        }

        if (patches.isEmpty()) {
            // Every cell came out transparent. Rather than drop the face and
            // leave a hole where the game draws something, keep it whole and
            // let the caller fall back to its own colour.
            return List.of(new Patch(positions.clone(), BlockTexture.TRANSPARENT));
        }
        return patches;
    }

    /**
     * How many cells one axis is worth cutting into.
     *
     * <p>Bounded from both ends: never finer than the setting asks for, and
     * never finer than {@link #SMALLEST_CELL} allows. A stair shows a face only
     * a few pixels tall, and cutting that into four is reading single pixels.
     */
    private static int cells(float[] texels, int from, int to, int detail) {
        float du = texels[to] - texels[from];
        float dv = texels[to + 1] - texels[from + 1];
        int span = Math.round((float) Math.sqrt(du * du + dv * dv));
        return Math.max(1, Math.min(span / SMALLEST_CELL, detail));
    }

    /** The colour of one cell, averaged over the texture it shows. */
    private static int colourOf(float[] texels, BlockTexture texture,
                                int column, int columns, int row, int rows) {
        float s0 = (float) column / columns;
        float s1 = (float) (column + 1) / columns;
        float t0 = (float) row / rows;
        float t1 = (float) (row + 1) / rows;

        float minU = Float.MAX_VALUE;
        float minV = Float.MAX_VALUE;
        float maxU = -Float.MAX_VALUE;
        float maxV = -Float.MAX_VALUE;
        for (float s : new float[] {s0, s1}) {
            for (float t : new float[] {t0, t1}) {
                float u = interpolate(texels, 0, s, t);
                float v = interpolate(texels, 1, s, t);
                minU = Math.min(minU, u);
                maxU = Math.max(maxU, u);
                minV = Math.min(minV, v);
                maxV = Math.max(maxV, v);
            }
        }

        return texture.average(
                (int) Math.floor(minU), (int) Math.floor(minV),
                (int) Math.ceil(maxU), (int) Math.ceil(maxV));
    }

    /**
     * Reads a point on the face.
     *
     * <p>Bilinear across the four corners, which is exact for the flat
     * four sided faces a block model is made of.
     *
     * @param stride how many numbers each corner has
     * @param offset which of them to read
     */
    private static float at(float[] values, int stride, int offset, int corner) {
        return values[corner * stride + offset];
    }

    private static float interpolate(float[] texels, int offset, float s, float t) {
        float top = lerp(at(texels, 2, offset, 0), at(texels, 2, offset, 1), s);
        float bottom = lerp(at(texels, 2, offset, 3), at(texels, 2, offset, 2), s);
        return lerp(top, bottom, t);
    }

    private static float[] cornersOf(float[] positions, float s0, float s1, float t0, float t1) {
        float[] vertices = new float[12];
        float[][] corners = {{s0, t0}, {s1, t0}, {s1, t1}, {s0, t1}};
        for (int corner = 0; corner < 4; corner++) {
            for (int axis = 0; axis < 3; axis++) {
                float top = lerp(at(positions, 3, axis, 0), at(positions, 3, axis, 1), corners[corner][0]);
                float bottom = lerp(at(positions, 3, axis, 3), at(positions, 3, axis, 2), corners[corner][0]);
                vertices[corner * 3 + axis] = lerp(top, bottom, corners[corner][1]);
            }
        }
        return vertices;
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    /** A rectangle of cells that came out the same colour. */
    private record Block(int row0, int row1, int column0, int column1, int colour) {
    }

    /**
     * Joins neighbouring cells of the same colour back together.
     *
     * <p>Along each row first, then whole rows that came out identical. Two
     * passes rather than a general merge: the changes in a block texture run in
     * bands -- a flame above a stick, a handle beside a door -- and two passes
     * catch those while staying easy to follow.
     */
    private static List<Block> merge(int[][] colours, int rows, int columns) {
        List<List<Block>> runs = new ArrayList<>(rows);
        for (int row = 0; row < rows; row++) {
            List<Block> line = new ArrayList<>();
            int start = 0;
            for (int column = 1; column <= columns; column++) {
                boolean end = column == columns || !alike(colours[row][column], colours[row][start]);
                if (end) {
                    line.add(new Block(row, row, start, column - 1, colours[row][start]));
                    start = column;
                }
            }
            runs.add(line);
        }

        List<Block> merged = new ArrayList<>();
        int row = 0;
        while (row < rows) {
            List<Block> line = runs.get(row);
            int last = row;
            while (last + 1 < rows && sameShape(line, runs.get(last + 1))) {
                last++;
            }
            for (Block block : line) {
                merged.add(new Block(row, last, block.column0, block.column1, block.colour));
            }
            row = last + 1;
        }
        return merged;
    }

    private static boolean sameShape(List<Block> a, List<Block> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            Block left = a.get(i);
            Block right = b.get(i);
            if (left.column0 != right.column0 || left.column1 != right.column1
                    || !alike(left.colour, right.colour)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether two colours are close enough to count as one.
     *
     * <p>Public because the same question is asked of whole materials later:
     * two faces of one texture average to slightly different values, and
     * keeping both as separate materials would fill the file with colours
     * nobody can tell apart. One definition, asked in both places.
     */
    public static boolean alike(int a, int b) {
        if (a == b) {
            return true;
        }
        // Transparent is not a colour, so it never blends into one.
        if (a == BlockTexture.TRANSPARENT || b == BlockTexture.TRANSPARENT) {
            return false;
        }
        return Math.abs(((a >> 16) & 0xff) - ((b >> 16) & 0xff)) <= SAME_COLOUR
                && Math.abs(((a >> 8) & 0xff) - ((b >> 8) & 0xff)) <= SAME_COLOUR
                && Math.abs((a & 0xff) - (b & 0xff)) <= SAME_COLOUR;
    }
}
