package de.tobi.voxelprint.export;

import java.util.Objects;

/**
 * One face of a block's real model.
 *
 * <p>Four corners in block-local coordinates, wound the way Minecraft wound
 * them, plus the material the face is made of. This is the difference between
 * "a torch is a small box" and "a torch is a stick with a flame on top": a
 * bounding box says how much space a block takes, a quad says what it looks
 * like.
 *
 * @param material  index into the export's material list
 * @param direction the face this quad belongs to, or {@code null} when the
 *                  model does not tie it to one
 * @param vertices  twelve numbers: four corners as x, y, z
 */
public record ModelQuad(int material, String direction, float[] vertices) {

    public ModelQuad {
        Objects.requireNonNull(vertices, "vertices");
        if (vertices.length != 12) {
            throw new IllegalArgumentException("A quad has four corners, so twelve numbers, not " + vertices.length);
        }
        vertices = vertices.clone();
    }

    @Override
    public float[] vertices() {
        // A record hands out the array itself, which would let a caller edit
        // the model after the fact.
        return vertices.clone();
    }
}
