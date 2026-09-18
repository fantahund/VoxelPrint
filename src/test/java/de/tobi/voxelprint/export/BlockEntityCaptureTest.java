package de.tobi.voxelprint.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for reading the part of a block the game draws itself.
 *
 * <p>These bake the real vanilla model rather than a stand-in.
 * {@code EntityModelSet.vanilla()} is a plain static and baking is arithmetic,
 * so the bell can be measured here without a running game -- which is the whole
 * point, because the thing worth checking is whether the numbers land in the
 * block the right way up.
 *
 * <p>The expected numbers come from the layer definition itself:
 * {@code bell_body} is a box from (-3,-6,-3) sized 6x7x6 at pivot (8,12,8), and
 * {@code bell_base} a box from (4,4,4) sized 8x2x8 whose pivot cancels its
 * parent's. Over sixteen, that is a body from y 6/16 to 13/16 and a skirt from
 * 4/16 to 6/16. The bar of the block model {@code block/bell_floor.json} runs
 * from 13/16 to 15/16, so the body hangs exactly under it.
 */
class BlockEntityCaptureTest {

    /** A sixteenth of a block, near enough for numbers that come from eighths. */
    private static final float TOLERANCE = 1e-4f;

    /**
     * Fills the registries {@code Blocks} needs before it will load.
     *
     * <p>Nothing here starts a game or opens a window. The registries are
     * ordinary maps and baking a model is arithmetic, which is what lets the
     * bell be measured in a unit test at all.
     */
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    @DisplayName("a bell is drawn by its renderer, a cobblestone is not")
    void knowsWhichBlocksAreDrawn() {
        assertTrue(BlockEntityCapture.draws(Blocks.BELL.defaultBlockState()));
        assertFalse(BlockEntityCapture.draws(Blocks.COBBLESTONE.defaultBlockState()));
        assertTrue(
            BlockEntityCapture.facesOf(Blocks.COBBLESTONE.defaultBlockState(), EntityModelSet.vanilla())
                .isEmpty(),
            "a block nothing is known about must add nothing");
    }

    @Test
    @DisplayName("the bell's body and skirt come out as twelve faces")
    void readsEveryFace() {
        List<BlockEntityCapture.Face> faces =
            BlockEntityCapture.facesOf(Blocks.BELL.defaultBlockState(), EntityModelSet.vanilla());

        // Two cubes, six faces each. Fewer would mean a part was missed; more
        // would mean something was counted twice.
        assertEquals(12, faces.size());
    }

    @Test
    @DisplayName("the bell hangs under the bar rather than above it")
    void standsTheRightWayUp() {
        List<BlockEntityCapture.Face> faces =
            BlockEntityCapture.facesOf(Blocks.BELL.defaultBlockState(), EntityModelSet.vanilla());

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (BlockEntityCapture.Face face : faces) {
            float[] positions = face.positions();
            for (int corner = 0; corner < 4; corner++) {
                minX = Math.min(minX, positions[corner * 3]);
                maxX = Math.max(maxX, positions[corner * 3]);
                minY = Math.min(minY, positions[corner * 3 + 1]);
                maxY = Math.max(maxY, positions[corner * 3 + 1]);
                minZ = Math.min(minZ, positions[corner * 3 + 2]);
                maxZ = Math.max(maxZ, positions[corner * 3 + 2]);
            }
        }

        // The skirt is the widest part and the body the tallest.
        assertEquals(4.0F / 16.0F, minX, TOLERANCE);
        assertEquals(12.0F / 16.0F, maxX, TOLERANCE);
        assertEquals(4.0F / 16.0F, minZ, TOLERANCE);
        assertEquals(12.0F / 16.0F, maxZ, TOLERANCE);

        // The two that matter. Upside down, the bell would reach 18/16 and
        // stand above the bar and outside its own block.
        assertEquals(4.0F / 16.0F, minY, TOLERANCE);
        assertEquals(13.0F / 16.0F, maxY, TOLERANCE);
    }

    @Test
    @DisplayName("every corner sits inside its own block")
    void staysInsideTheBlock() {
        List<BlockEntityCapture.Face> faces =
            BlockEntityCapture.facesOf(Blocks.BELL.defaultBlockState(), EntityModelSet.vanilla());

        for (BlockEntityCapture.Face face : faces) {
            for (float value : face.positions()) {
                assertTrue(value >= -TOLERANCE && value <= 1.0F + TOLERANCE,
                    "a corner at " + value + " has left the block");
            }
            // Texture coordinates run from zero to one; ModelCapture scales
            // them by the texture's own size, and anything outside would fold
            // the wrong pixels into the colour.
            for (float value : face.texels()) {
                assertTrue(value >= -TOLERANCE && value <= 1.0F + TOLERANCE,
                    "a texture coordinate of " + value + " is off the texture");
            }
        }
    }
}
