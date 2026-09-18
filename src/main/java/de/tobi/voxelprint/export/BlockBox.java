package de.tobi.voxelprint.export;

/**
 * One axis-aligned box of a block's shape, in block-local coordinates.
 *
 * <p>Zero is one face of the block and one is the opposite face, so a full cube
 * is {@code (0,0,0)-(1,1,1)} and a bottom slab is {@code (0,0,0)-(1,0.5,1)}.
 * Independent of where the block sits in the world, which is what lets one shape
 * be shared by every block of the same state.
 */
public record BlockBox(
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ) {

    /** The whole block, used when nothing more specific is known. */
    public static final BlockBox FULL = new BlockBox(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

    /**
     * Cleans floating point noise.
     *
     * <p>Most Minecraft models sit on a sixteenth grid, but the coordinates
     * arrive as doubles and print as {@code 0.30000000000000004}. Rounding to
     * six decimals keeps the file readable and makes two shapes that are meant
     * to be identical actually compare equal.
     *
     * <p>Six decimals rather than snapping to sixteenths: not every block obeys
     * that grid, and forcing them onto it would quietly distort the ones that
     * do not.
     */
    public static BlockBox rounded(double minX, double minY, double minZ,
                                   double maxX, double maxY, double maxZ) {
        return new BlockBox(snap(minX), snap(minY), snap(minZ), snap(maxX), snap(maxY), snap(maxZ));
    }

    private static double snap(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }

    /** True when this box is the entire block. */
    public boolean isFullBlock() {
        return minX == 0.0 && minY == 0.0 && minZ == 0.0 && maxX == 1.0 && maxY == 1.0 && maxZ == 1.0;
    }
}
