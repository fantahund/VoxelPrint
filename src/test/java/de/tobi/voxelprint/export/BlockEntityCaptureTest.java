package de.tobi.voxelprint.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for turning a posed entity model into faces inside a block.
 *
 * <p>Which model a block needs, and where the game puts it, is no longer
 * written down anywhere: the export runs the real block entity renderer and
 * writes down what it submits. That half needs a running game and is not
 * testable here. The half that is, is the arithmetic -- a baked model and a
 * pose in, corners of the block out -- and that is what these measure, on the
 * two models most likely to come out wrong.
 *
 * <p>{@code EntityModelSet.vanilla()} is a plain static and baking is
 * arithmetic, so both can be measured without a window ever opening.
 */
class BlockEntityCaptureTest {

    /** A sixteenth of a block, near enough for numbers that come from eighths. */
    private static final float TOLERANCE = 1e-4f;

    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath("minecraft", "entity/bell/bell_body");

    /**
     * Fills the registries the model layers need before they will load.
     *
     * <p>Nothing here starts a game or opens a window.
     */
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ModelPart bake(ModelLayerLocation layer) {
        return EntityModelSet.vanilla().bakeLayer(layer);
    }

    private static float[] bounds(List<BlockEntityCapture.Face> faces) {
        float[] bounds = {
            Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
            -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE,
        };
        for (BlockEntityCapture.Face face : faces) {
            float[] positions = face.positions();
            for (int corner = 0; corner < 4; corner++) {
                for (int axis = 0; axis < 3; axis++) {
                    float value = positions[corner * 3 + axis];
                    bounds[axis] = Math.min(bounds[axis], value);
                    bounds[axis + 3] = Math.max(bounds[axis + 3], value);
                }
            }
        }
        return bounds;
    }

    /**
     * The bell, which {@code BellRenderer} draws with no pose of its own.
     *
     * <p>The expected numbers come from the layer definition itself:
     * {@code bell_body} is a box from (-3,-6,-3) sized 6x7x6 at pivot (8,12,8),
     * and {@code bell_base} a box from (4,4,4) sized 8x2x8 whose pivot cancels
     * its parent's. Over sixteen, that is a body from y 6/16 to 13/16 and a
     * skirt from 4/16 to 6/16. The bar of the block model
     * {@code block/bell_floor.json} runs from 13/16 to 15/16, so the body hangs
     * exactly under it.
     */
    @Test
    @DisplayName("the bell's body and skirt come out as twelve faces, under the bar")
    void readsTheBell() {
        List<BlockEntityCapture.Face> faces =
            BlockEntityCapture.facesOf(bake(ModelLayers.BELL), new PoseStack(), TEXTURE);

        // Two cubes, six faces each. Fewer would mean a part was missed; more
        // would mean something was counted twice.
        assertEquals(12, faces.size());

        float[] bounds = bounds(faces);
        // The skirt is the widest part and the body the tallest.
        assertEquals(4.0F / 16.0F, bounds[0], TOLERANCE);
        assertEquals(4.0F / 16.0F, bounds[2], TOLERANCE);
        assertEquals(12.0F / 16.0F, bounds[3], TOLERANCE);
        assertEquals(12.0F / 16.0F, bounds[5], TOLERANCE);

        // The two that matter. Upside down, the bell would reach 18/16 and
        // stand above the bar and outside its own block.
        assertEquals(4.0F / 16.0F, bounds[1], TOLERANCE);
        assertEquals(13.0F / 16.0F, bounds[4], TOLERANCE);
    }

    @Test
    @DisplayName("every corner of the bell sits inside its own block")
    void staysInsideTheBlock() {
        List<BlockEntityCapture.Face> faces =
            BlockEntityCapture.facesOf(bake(ModelLayers.BELL), new PoseStack(), TEXTURE);

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

    /**
     * The copper golem statue, which has no block model at all.
     *
     * <p>{@code block/copper_golem_statue.json} names a particle texture and
     * nothing else: every face of it comes from the entity model, which is why
     * the statue used to export as an empty block. Its model is built the way
     * an entity's is -- hanging from a root at 24 pixels, y pointing down --
     * and {@code CopperGolemStatueModel.setupAnim} is what turns it up the
     * right way: the root to zero and a half turn about z. The renderer's own
     * transformation then moves it into the middle of the block and faces it.
     *
     * <p>It is a block and a half tall and reaches out of its own block, which
     * is what the game draws: the statue shares the golem's model, and the
     * golem's ears are above its shoulders.
     */
    @Test
    @DisplayName("the copper golem statue stands on the floor of its block, facing out")
    void readsTheCopperGolemStatue() {
        ModelPart root = bake(ModelLayers.COPPER_GOLEM);
        // What CopperGolemStatueModel.setupAnim does, and the only reason the
        // statue is not upside down and hanging under the floor.
        root.y = 0.0F;
        root.zRot = (float) Math.PI;

        PoseStack poseStack = new PoseStack();
        // What CopperGolemStatueBlockRenderer.modelTransformation does for a
        // statue facing north: into the middle of the block, then turned.
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.rotate(Axis.YP.rotationDegrees(-Direction.NORTH.getOpposite().toYRot()));

        List<BlockEntityCapture.Face> faces =
            BlockEntityCapture.facesOf(root, poseStack, TEXTURE);

        // Nine cubes -- body, head with its ears and antenna, two arms, two
        // legs -- six faces each.
        assertEquals(54, faces.size());

        float[] bounds = bounds(faces);
        // Standing on the floor, not hanging under it, which is what the half
        // turn about z is there to prevent.
        assertEquals(0.0F, bounds[1], 1.0F / 16.0F);
        assertEquals(24.0F / 16.0F, bounds[4], 1.0F / 16.0F);

        // Centred across the block. A statue that is off to one side means the
        // half block the renderer moves it by was applied before the turn
        // rather than after.
        assertEquals(1.0F / 16.0F, bounds[0], TOLERANCE);
        assertEquals(15.0F / 16.0F, bounds[3], TOLERANCE);
        assertTrue(bounds[2] >= -TOLERANCE && bounds[5] <= 1.0F + TOLERANCE,
            "the statue is " + bounds[2] + " to " + bounds[5] + " deep, which is wider than its block");
    }
}
