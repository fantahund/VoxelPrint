package de.tobi.voxelprint.export;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.tobi.voxelprint.VoxelPrint;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColorCollection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the part of a block that a block entity renderer draws.
 *
 * <p>A block model stops at what makes sense to bake once. Anything that moves,
 * like a chest's lid or a bell's body, comes from a separate entity model, and
 * a block that was swinging when the export ran still exports hanging straight,
 * which is the only sensible thing to print.
 *
 * <p>Must be called on the client thread.
 */
public final class BlockEntityCapture {

    /**
     * What a block entity renderer adds to a block, and where its pixels live.
     *
     * @param layer   the entity model layer the renderer bakes
     * @param texture the sprite it draws with, taken from the renderer itself
     *                rather than written out again here
     */
    private record Drawn(ModelLayerLocation layer, Identifier texture) {
    }

    /**
     * The blocks this knows about.
     *
     * <p>Every block entity needs its own placement, because their models are
     * built facing one way and the renderer turns them to match the block state.
     * The bell needs none: its body and skirt are both square about the vertical
     * axis, so all four facings come out the same.
     */
    private static final Map<Block, Drawn> DRAWN = new HashMap<>();

    private static void add(Block block, ModelLayerLocation layer, String texture) {
        DRAWN.put(block, new Drawn(layer, Identifier.fromNamespaceAndPath("minecraft", texture)));
    }

    static {
        add(Blocks.BELL, ModelLayers.BELL, "entity/bell/bell_body");
        
        add(Blocks.DRAGON_HEAD, ModelLayers.DRAGON_SKULL, "entity/enderdragon/dragon");
        add(Blocks.DRAGON_WALL_HEAD, ModelLayers.DRAGON_SKULL, "entity/enderdragon/dragon");
        add(Blocks.SKELETON_SKULL, ModelLayers.SKELETON_SKULL, "entity/skeleton/skeleton");
        add(Blocks.SKELETON_WALL_SKULL, ModelLayers.SKELETON_SKULL, "entity/skeleton/skeleton");
        add(Blocks.WITHER_SKELETON_SKULL, ModelLayers.WITHER_SKELETON_SKULL, "entity/skeleton/wither_skeleton");
        add(Blocks.WITHER_SKELETON_WALL_SKULL, ModelLayers.WITHER_SKELETON_SKULL, "entity/skeleton/wither_skeleton");
        add(Blocks.ZOMBIE_HEAD, ModelLayers.ZOMBIE_HEAD, "entity/zombie/zombie");
        add(Blocks.ZOMBIE_WALL_HEAD, ModelLayers.ZOMBIE_HEAD, "entity/zombie/zombie");
        add(Blocks.CREEPER_HEAD, ModelLayers.CREEPER_HEAD, "entity/creeper/creeper");
        add(Blocks.CREEPER_WALL_HEAD, ModelLayers.CREEPER_HEAD, "entity/creeper/creeper");
        add(Blocks.PIGLIN_HEAD, ModelLayers.PIGLIN_HEAD, "entity/piglin/piglin");
        add(Blocks.PIGLIN_WALL_HEAD, ModelLayers.PIGLIN_HEAD, "entity/piglin/piglin");
        add(Blocks.PLAYER_HEAD, ModelLayers.PLAYER_HEAD, "entity/player/wide/steve");
        add(Blocks.PLAYER_WALL_HEAD, ModelLayers.PLAYER_HEAD, "entity/player/wide/steve");
        
        add(Blocks.CHEST, ModelLayers.CHEST, "entity/chest/normal");
        add(Blocks.ENDER_CHEST, ModelLayers.CHEST, "entity/chest/ender");
        add(Blocks.TRAPPED_CHEST, ModelLayers.CHEST, "entity/chest/trapped");
        
        add(Blocks.SHULKER_BOX, ModelLayers.SHULKER_BOX, "entity/shulker/shulker");
        ColorCollection.VALUES.forEach(color -> {
            add(Blocks.DYED_SHULKER_BOX.pick(color), ModelLayers.SHULKER_BOX, "entity/shulker/" + color.getName());
        });
    }

    /**
     * One face of an entity model, before it has been given a material.
     *
     * @param texture   the texture it is drawn with
     * @param positions twelve numbers: four corners as x, y, z, block-local
     * @param texels    eight numbers: four corners as u, v, from zero to one
     */
    public record Face(Identifier texture, float[] positions, float[] texels) {
    }

    private BlockEntityCapture() {
        throw new AssertionError("No instances.");
    }

    /** Whether anything here would add to this block. */
    public static boolean draws(BlockState state) {
        return DRAWN.containsKey(state.getBlock());
    }

    /**
     * Collects the faces the block entity renderer would draw.
     *
     * <p>Returns an empty list for a block nothing is known about, and for one
     * whose model could not be baked. A block entity that cannot be read is a
     * block that comes out as it did before this existed, which is worse than
     * the alternative but better than a failed export.
     */
    public static List<Face> facesOf(BlockState state, EntityModelSet models) {
        Drawn drawn = DRAWN.get(state.getBlock());
        if (drawn == null) {
            return List.of();
        }

        try {
            ModelLayerLocation layer = drawn.layer();
            if (state.hasProperty(BlockStateProperties.CHEST_TYPE)) {
                ChestType type = state.getValue(BlockStateProperties.CHEST_TYPE);
                if (type == ChestType.LEFT) {
                    layer = ModelLayers.DOUBLE_CHEST_LEFT;
                } else if (type == ChestType.RIGHT) {
                    layer = ModelLayers.DOUBLE_CHEST_RIGHT;
                }
            }

            ModelPart part = models.bakeLayer(layer);
            List<Face> faces = new ArrayList<>();
            Identifier texture = drawn.texture();

            PoseStack poseStack = new PoseStack();
            poseStack.translate(0.5f, 0.0f, 0.5f);

            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
                float angle = switch (facing) {
                    case SOUTH -> 180f;
                    case WEST -> 90f;
                    case EAST -> 270f;
                    default -> 0f;
                };

                Block block = state.getBlock();
                if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.ENDER_CHEST) {
                    angle += 180f;
                } else {
                    angle += 180f;
                }
                
                poseStack.rotate(Axis.YP.rotationDegrees(angle));
            } else if (state.hasProperty(BlockStateProperties.ROTATION_16)) {
                int rotation = state.getValue(BlockStateProperties.ROTATION_16);
                float angle = (rotation * 360.0F) / 16.0F;
                poseStack.rotate(Axis.YP.rotationDegrees(-angle));
            }
            
            poseStack.translate(-0.5f, 0.0f, -0.5f);

            part.visit(poseStack, (pose, path, index, cube) -> {
                Matrix4f matrix = pose.pose();
                for (ModelPart.Polygon polygon : cube.polygons) {
                    ModelPart.Vertex[] corners = polygon.vertices();
                    if (corners.length != 4) {
                        // Every cube face is a quad; anything else is not
                        // something this knows how to carry.
                        continue;
                    }
                    faces.add(faceOf(texture, matrix, corners));
                }
            });
            return faces;
        } catch (RuntimeException e) {
            VoxelPrint.LOGGER.warn("Could not read the block entity model of {}", state, e);
            return List.of();
        }
    }

    private static Face faceOf(Identifier texture, Matrix4f matrix, ModelPart.Vertex[] corners) {
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
        return new Face(texture, positions, texels);
    }
}
