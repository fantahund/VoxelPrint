package de.tobi.voxelprint.export;

import com.mojang.blaze3d.vertex.PoseStack;
import de.tobi.voxelprint.VoxelPrint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Reads the part of a block that a block entity renderer draws.
 *
 * <p>A block model stops at what makes sense to bake once. Anything that moves
 * or is built from more than a block state -- a chest's lid, a bell's body, a
 * copper golem's whole body, the pattern on a banner -- is drawn by a block
 * entity renderer instead, and a block exported from its model alone comes out
 * as a frame with nothing in it.
 *
 * <p>Rather than keep a list of which block needs which model at which angle,
 * this runs the game's own renderer and writes down what it asks for. The
 * renderer is handed a collector that draws nothing: every call is ignored
 * except the ones that submit a model, and those are walked for their corners.
 * That is the only way to be right about a placement without copying it, and
 * copying it is how the list went wrong -- a guessed rotation looks like a
 * working one until somebody prints it.
 *
 * <p>A shape captured this way is the shape at rest, near enough. Openness,
 * swing and spin all come off the block entity, and a chest that happened to be
 * open when the export ran exports open; that is what the player saw.
 *
 * <p>Must be called on the client thread.
 */
public final class BlockEntityCapture {

    /**
     * One face of an entity model, before it has been given a material.
     *
     * @param texture   the texture it is drawn with
     * @param sprite    the atlas sprite it was stitched into, or null for a
     *                  texture that stands on its own
     * @param positions twelve numbers: four corners as x, y, z, block-local
     * @param texels    eight numbers: four corners as u, v, from zero to one
     */
    public record Face(Identifier texture, TextureAtlasSprite sprite, float[] positions, float[] texels) {
    }

    /**
     * Block entities whose renderer draws something that is not the block.
     *
     * <p>A spawner draws the mob turning inside it, and a mob is not part of
     * the build. An enchanting table draws its book a block above itself, with
     * nothing in between: faithful on screen, and an island in mid air once it
     * is a mesh.
     */
    private static final Set<BlockEntityType<?>> SKIPPED = Set.of(
            BlockEntityTypes.MOB_SPAWNER,
            BlockEntityTypes.TRIAL_SPAWNER,
            BlockEntityTypes.ENCHANTING_TABLE);

    /**
     * Where to look for a colour when the renderer names no texture.
     *
     * <p>Almost every renderer hands over either a texture or the atlas sprite
     * it was stitched into. Skulls are the exception: they pick a render type
     * built around the skin and pass only that, and a render type keeps its
     * texture to itself. These are the skins the game would have used.
     */
    private static final Map<Block, String> TEXTURE_HINTS = new HashMap<>();

    /**
     * Stands in for a texture nothing named.
     *
     * <p>Without a texture there is still a shape, and a grey shape prints
     * better than a hole does. {@link ModelCapture} reads this as a texture it
     * cannot find and falls back to a colour of its own.
     */
    private static final Identifier UNKNOWN =
            Identifier.fromNamespaceAndPath("voxelprint", "unknown");

    private static void hint(Block block, String texture) {
        TEXTURE_HINTS.put(block, texture);
    }

    static {
        hint(Blocks.SKELETON_SKULL, "entity/skeleton/skeleton");
        hint(Blocks.SKELETON_WALL_SKULL, "entity/skeleton/skeleton");
        hint(Blocks.WITHER_SKELETON_SKULL, "entity/skeleton/wither_skeleton");
        hint(Blocks.WITHER_SKELETON_WALL_SKULL, "entity/skeleton/wither_skeleton");
        hint(Blocks.ZOMBIE_HEAD, "entity/zombie/zombie");
        hint(Blocks.ZOMBIE_WALL_HEAD, "entity/zombie/zombie");
        hint(Blocks.CREEPER_HEAD, "entity/creeper/creeper");
        hint(Blocks.CREEPER_WALL_HEAD, "entity/creeper/creeper");
        hint(Blocks.PIGLIN_HEAD, "entity/piglin/piglin");
        hint(Blocks.PIGLIN_WALL_HEAD, "entity/piglin/piglin");
        hint(Blocks.DRAGON_HEAD, "entity/enderdragon/dragon");
        hint(Blocks.DRAGON_WALL_HEAD, "entity/enderdragon/dragon");
        // A player head wears a skin that is fetched from the network and is
        // not a resource at all; the default is what the game shows until it
        // arrives, and what it shows for a head with no owner.
        hint(Blocks.PLAYER_HEAD, "entity/player/wide/steve");
        hint(Blocks.PLAYER_WALL_HEAD, "entity/player/wide/steve");
    }

    private BlockEntityCapture() {
        throw new AssertionError("No instances.");
    }

    /**
     * Collects the faces the block entity renderer would draw.
     *
     * <p>Returns an empty list where there is no block entity, no renderer for
     * it, or the renderer would not run outside a frame. A block entity that
     * cannot be read is a block that comes out as it did before this existed,
     * which is worse than the alternative but better than a failed export.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<Face> facesOf(Minecraft client, ClientLevel level, BlockPos position) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity == null || SKIPPED.contains(blockEntity.getType())) {
            return List.of();
        }

        BlockEntityRenderDispatcher dispatcher = client.getBlockEntityRenderDispatcher();
        BlockEntityRenderer renderer = dispatcher.getRenderer(blockEntity);
        if (renderer == null) {
            return List.of();
        }

        Identifier hint = hintFor(blockEntity);
        FaceCollector collector = new FaceCollector(hint);
        try {
            // The camera sits in the block. Renderers that thin out with
            // distance -- a sign's text, a skull's second layer -- then draw
            // everything they have, which is what should be printed.
            Vec3 eye = Vec3.atCenterOf(position);
            dispatcher.prepare(eye);

            BlockEntityRenderState state = (BlockEntityRenderState) renderer.createRenderState();
            renderer.extractRenderState(blockEntity, state, 0.0F, eye, null);

            PoseStack poseStack = new PoseStack();
            renderer.submit(state, poseStack, collector, camera(position));
        } catch (RuntimeException | LinkageError e) {
            VoxelPrint.LOGGER.warn("Could not read the block entity model of {} at {}",
                    blockEntity.getType(), position, e);
            return List.of();
        }
        return collector.faces();
    }

    private static Identifier hintFor(BlockEntity blockEntity) {
        String texture = TEXTURE_HINTS.get(blockEntity.getBlockState().getBlock());
        return texture == null ? null : Identifier.fromNamespaceAndPath("minecraft", texture);
    }

    /**
     * Just enough camera for a renderer to ask questions of.
     *
     * <p>Only the position is ever read by a block entity renderer, and only to
     * decide how much detail to draw.
     */
    private static CameraRenderState camera(BlockPos position) {
        CameraRenderState camera = new CameraRenderState();
        camera.blockPos = position.immutable();
        camera.pos = Vec3.atCenterOf(position);
        camera.orientation = new Quaternionf();
        camera.initialized = true;
        return camera;
    }

    /**
     * A renderer's audience, which writes down instead of drawing.
     *
     * <p>Everything a renderer can submit is here so that the interface is
     * satisfied; almost all of it is deliberately dropped. Text, items, flames,
     * name tags, particles and break overlays are not geometry of the block,
     * and the pieces of the world the renderer submits by reference -- moving
     * blocks, block models -- the export already has from elsewhere.
     */
    private static final class FaceCollector implements SubmitNodeCollector {

        private final List<Face> faces = new ArrayList<>();
        private final Identifier hint;

        FaceCollector(Identifier hint) {
            this.hint = hint;
        }

        List<Face> faces() {
            return List.copyOf(faces);
        }

        // -- what is kept -------------------------------------------------

        @Override
        public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack,
                                    RenderType renderType, int light, int overlay, int colour,
                                    UvMapping uvMapping, int outline) {
            model(model, state, poseStack, spriteOf(uvMapping));
        }

        @Override
        public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack,
                                    RenderType renderType, int light, int overlay, int colour) {
            model(model, state, poseStack, null);
        }

        @Override
        public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack,
                                    Identifier texture, int light, int overlay, int colour) {
            model(model, state, poseStack, texture, null);
        }

        @Override
        public <S> void submitModel(Model<S> model, S state, PoseStack poseStack,
                                    int light, int overlay, int colour,
                                    SpriteId sprite, SpriteGetter sprites, int outline) {
            model(model, state, poseStack, sprite.texture(), null);
        }

        @Override
        public void submitModelPart(ModelPart part, PoseStack poseStack, RenderType renderType,
                                    int light, int overlay, UvMapping uvMapping) {
            part(part, poseStack, spriteOf(uvMapping));
        }

        @Override
        public void submitModelPart(ModelPart part, PoseStack poseStack, RenderType renderType,
                                    int light, int overlay, UvMapping uvMapping, int colour) {
            part(part, poseStack, spriteOf(uvMapping));
        }

        @Override
        public void submitModelPart(ModelPart part, PoseStack poseStack, RenderType renderType,
                                    int light, int overlay, UvMapping uvMapping, int colour, int outline) {
            part(part, poseStack, spriteOf(uvMapping));
        }

        // -- what is dropped ----------------------------------------------

        @Override
        public OrderedSubmitNodeCollector order(int order) {
            // The order only matters to something that draws in passes.
            return this;
        }

        @Override
        public <S> void submitCrumblingOverlay(Model<? super S> model, S state, PoseStack poseStack,
                                               RenderType renderType, int light, int overlay,
                                               int colour, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        }

        @Override
        public void submitCrumblingOverlay(ModelPart part, PoseStack poseStack, RenderType renderType,
                                           int light, int overlay, int colour,
                                           ModelFeatureRenderer.CrumblingOverlay crumbling) {
        }

        @Override
        public void submitShadow(PoseStack poseStack, float alpha, List<EntityRenderState.ShadowPiece> pieces) {
        }

        @Override
        public void submitNameTag(PoseStack poseStack, Vec3 offset, int light, Component name,
                                  boolean discrete, int outline, CameraRenderState camera) {
        }

        @Override
        public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence text,
                               boolean dropShadow, Font.DisplayMode mode,
                               int light, int colour, int backgroundColour, int outlineColour) {
        }

        @Override
        public void submitTextBackground(PoseStack poseStack, float x, float y, float width, float height,
                                         int colour, Font.DisplayMode mode, int light) {
        }

        @Override
        public void submitFlame(PoseStack poseStack, EntityRenderState state, Quaternionf orientation) {
        }

        @Override
        public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leash) {
        }

        @Override
        public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState state, int light) {
        }

        @Override
        public void submitBlockModel(PoseStack poseStack, RenderType renderType,
                                     List<BlockStateModelPart> parts, int[] tints,
                                     int light, int overlay, int outline) {
        }

        @Override
        public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts,
                                             int light, boolean shaded) {
        }

        @Override
        public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType,
                                       int colour, float width, boolean depthTest) {
        }

        @Override
        public void submitItem(PoseStack poseStack, ItemDisplayContext context,
                               int light, int overlay, int outline, int[] tints,
                               ItemQuads quads, ItemStackRenderState.FoilType foil) {
        }

        @Override
        public void submitCustomGeometry(PoseStack poseStack, RenderType renderType,
                                         SubmitNodeCollector.CustomGeometryRenderer renderer) {
        }

        @Override
        public void submitQuadParticleGroup(QuadParticleRenderState particles) {
        }

        @Override
        public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group,
                                          CameraRenderState camera, boolean depthTest) {
        }

        // -- the work ------------------------------------------------------

        private static TextureAtlasSprite spriteOf(UvMapping uvMapping) {
            // Every mapping the game passes here is a sprite; it is what the
            // mapping is for. Anything else names no texture and falls back.
            return uvMapping instanceof TextureAtlasSprite sprite ? sprite : null;
        }

        private <S> void model(Model<? super S> model, S state, PoseStack poseStack, TextureAtlasSprite sprite) {
            model(model, state, poseStack, sprite == null ? null : sprite.contents().name(), sprite);
        }

        private <S> void model(Model<? super S> model, S state, PoseStack poseStack,
                               Identifier texture, TextureAtlasSprite sprite) {
            // The renderer submits the model and the pose it should be put in
            // separately; the pose is only applied when it is drawn. A copper
            // golem statue stands on its head without this.
            model.setupAnim(state);
            collect(model.root(), poseStack, texture, sprite);
        }

        private void part(ModelPart part, PoseStack poseStack, TextureAtlasSprite sprite) {
            collect(part, poseStack, sprite == null ? null : sprite.contents().name(), sprite);
        }

        private void collect(ModelPart root, PoseStack poseStack,
                             Identifier texture, TextureAtlasSprite sprite) {
            Identifier named = texture != null ? texture : hint;
            collectInto(faces, root, poseStack, named != null ? named : UNKNOWN, sprite);
        }
    }

    /**
     * Turns a posed model into faces, in the block the pose puts it in.
     *
     * <p>Split out from the collector because it is the half worth measuring:
     * given a model and a pose it is arithmetic, and arithmetic can be checked
     * without a running game.
     */
    static List<Face> facesOf(ModelPart root, PoseStack poseStack, Identifier texture) {
        List<Face> faces = new ArrayList<>();
        collectInto(faces, root, poseStack, texture, null);
        return faces;
    }

    private static void collectInto(List<Face> faces, ModelPart root, PoseStack poseStack,
                                    Identifier texture, TextureAtlasSprite sprite) {
        Map<String, Boolean> shown = new HashMap<>();
        root.visit(poseStack, (pose, path, index, cube) -> {
            if (!visible(root, path, shown)) {
                return;
            }
            Matrix4f matrix = pose.pose();
            for (ModelPart.Polygon polygon : cube.polygons) {
                ModelPart.Vertex[] corners = polygon.vertices();
                if (corners.length != 4) {
                    // Every cube face is a quad; anything else is not something
                    // this knows how to carry.
                    continue;
                }
                faces.add(faceOf(texture, sprite, matrix, corners));
            }
        });
    }

    /**
     * Whether the part this cube belongs to is one the game would draw.
     *
     * <p>Models turn parts off rather than build a second model: a banner on a
     * wall is the standing banner with its post hidden, and a head with no
     * second layer is the same model with that layer off. Walking the model
     * from outside is not possible -- its children are private -- but the path
     * the visitor is given names them, and a part can be looked up by name.
     */
    private static boolean visible(ModelPart root, String path, Map<String, Boolean> shown) {
        Boolean known = shown.get(path);
        if (known != null) {
            return known;
        }

        boolean result = true;
        ModelPart part = root;
        if (!root.visible || root.skipDraw) {
            result = false;
        } else {
            for (String name : path.split("/")) {
                if (name.isEmpty()) {
                    continue;
                }
                if (!part.hasChild(name)) {
                    // A name that cannot be resolved is not a reason to drop
                    // geometry the renderer asked for.
                    break;
                }
                part = part.getChild(name);
                if (!part.visible || part.skipDraw) {
                    result = false;
                    break;
                }
            }
        }

        shown.put(path, result);
        return result;
    }

    private static Face faceOf(Identifier texture, TextureAtlasSprite sprite,
                               Matrix4f matrix, ModelPart.Vertex[] corners) {
        float[] positions = new float[12];
        float[] texels = new float[8];
        Vector3f point = new Vector3f();

        for (int corner = 0; corner < 4; corner++) {
            ModelPart.Vertex vertex = corners[corner];
            matrix.transformPosition(vertex.worldX(), vertex.worldY(), vertex.worldZ(), point);
            positions[corner * 3] = point.x();
            positions[corner * 3 + 1] = point.y();
            positions[corner * 3 + 2] = point.z();
            texels[corner * 2] = vertex.u();
            texels[corner * 2 + 1] = vertex.v();
        }
        return new Face(texture, sprite, positions, texels);
    }
}
