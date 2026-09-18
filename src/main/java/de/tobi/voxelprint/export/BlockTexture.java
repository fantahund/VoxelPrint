package de.tobi.voxelprint.export;

/**
 * The pixels of one block texture, kept only long enough to measure colours
 * from.
 *
 * <p>Never exported and never written anywhere: the file format carries derived
 * colours, not textures. This exists so a region of a face can be asked what
 * colour it is, which is the one thing a printer needs and the whole texture
 * cannot answer -- a torch averaged as a whole is brown, and the flame on top
 * of it disappears into that average.
 */
public final class BlockTexture {

    /** Returned where a region has no opaque pixels at all. */
    public static final int TRANSPARENT = -1;

    private final int width;
    private final int height;
    /** Packed ARGB, row by row. */
    private final int[] pixels;

    public BlockTexture(int width, int height, int[] pixels) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /**
     * Averages the opaque pixels of a region.
     *
     * <p>The bounds are clamped and the region is never empty: a face thinner
     * than a pixel still shows the pixel it lands on.
     *
     * <p>Transparent pixels are skipped rather than averaged in, because they
     * are not part of what gets printed. A region with nothing but those is
     * reported as {@link #TRANSPARENT}, which is the caller's cue to leave that
     * piece of the face out altogether: the texture shows nothing there, so
     * there is nothing to print.
     *
     * @param x1 one past the last column, as usual for a half open range
     * @param y1 one past the last row
     * @return the average as 0xRRGGBB, or {@link #TRANSPARENT}
     */
    public int average(int x0, int y0, int x1, int y1) {
        int left = clamp(x0, width);
        int top = clamp(y0, height);
        int right = Math.max(clamp(x1, width + 1), left + 1);
        int bottom = Math.max(clamp(y1, height + 1), top + 1);

        long red = 0;
        long green = 0;
        long blue = 0;
        long counted = 0;

        for (int y = top; y < bottom && y < height; y++) {
            for (int x = left; x < right && x < width; x++) {
                int pixel = pixels[y * width + x];
                if (((pixel >>> 24) & 0xff) < 128) {
                    continue;
                }
                red += (pixel >> 16) & 0xff;
                green += (pixel >> 8) & 0xff;
                blue += pixel & 0xff;
                counted++;
            }
        }

        if (counted == 0) {
            return TRANSPARENT;
        }
        return (int) ((red / counted) << 16 | (green / counted) << 8 | (blue / counted));
    }

    private static int clamp(int value, int limit) {
        return Math.max(0, Math.min(value, limit - 1));
    }
}
