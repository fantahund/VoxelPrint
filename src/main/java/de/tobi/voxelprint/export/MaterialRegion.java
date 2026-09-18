package de.tobi.voxelprint.export;

import java.util.Objects;

/**
 * One material a block model is made of.
 *
 * <p>A texture name and the average colour of that texture. This is what turns
 * a model into printable regions: a piston is not one colour, it is a wooden
 * casing, a stone body, a dark face and some redstone, and each of those is one
 * texture.
 *
 * <p>Only the name and a single averaged colour are exported, never the texture
 * itself. A few bytes that cannot be turned back into an image, which keeps
 * other people's texture packs and mod assets where they belong.
 *
 * <p>Some textures are stored without colour and are tinted when drawn: grass,
 * leaves and vines are grey in the file and take their green from the biome.
 * The tint is folded into the colour here, because the grey is not what anybody
 * sees and certainly not what should be printed. The same texture used with two
 * different tints is two materials, which is right: they print differently.
 *
 * @param texture the texture's name, for example {@code minecraft:block/oak_planks}
 * @param tint    the tint applied to it, as 0xRRGGBB, or -1 when untinted
 * @param colour  the colour to print, tint included, as 0xRRGGBB
 */
public record MaterialRegion(String texture, int tint, int colour) {

    /** Marks a material that takes its texture's colour unchanged. */
    public static final int NO_TINT = -1;

    public MaterialRegion {
        Objects.requireNonNull(texture, "texture");
    }
}
