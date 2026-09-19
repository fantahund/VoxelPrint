package de.tobi.voxelprint.export;

import com.mojang.blaze3d.platform.NativeImage;
import de.tobi.voxelprint.VoxelPrint;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

/**
 * Reads the real model of a block state.
 *
 * <p>Where {@link SnapshotCapture} asks how much space a block takes,
 * this asks what it looks like. A torch is a stick with a lit head, not a small
 * box of one colour; a piston has a wooden casing, a stone body and a dark
 * face. That information lives in Minecraft's baked models and in the textures
 * they point at, and nowhere else.
 *
 * <p>Faces are cut up to follow their texture, see {@link QuadSplitter}. One
 * colour per face would lose everything that is smaller than a face, which is
 * most of what makes a block recognisable.
 *
 * <p>Materials are shared across the whole export and collected here as they
 * are met, so the same texture and colour is described once however many blocks
 * use it.
 *
 * <p>Must be called on the client thread.
 */
public final class ModelCapture {

    /**
     * Fixed seed for models that pick a variant at random.
     *
     * <p>Grass and stone bricks come in several rotations chosen by position.
     * A fixed seed means the same build exports the same way twice, which
     * matters more here than the variety would.
     */
    private static final long VARIANT_SEED = 42L;

    /** Used where a texture cannot be read at all. */
    private static final int UNKNOWN_COLOUR = 0x9a9a9a;

    /**
     * A texture, a tint and a colour taken from part of it.
     *
     * <p>All three belong to the identity. Two parts of one texture print in
     * two colours, which is the whole point of cutting faces up; and one
     * texture under two biome tints is two materials as well, because grass in
     * a swamp and grass on a plain print differently.
     */
    private record MaterialKey(Identifier texture, int tint, int colour) {
    }

    private final Minecraft client;
    private final BlockColors blockColors;
    private final RandomSource random = RandomSource.create();
    private final int detail;

    private final Map<MaterialKey, Integer> materialIndices = new HashMap<>();
    /** Materials grouped by texture and tint, to find near matches within one. */
    private final Map<Identifier, List<Integer>> byTexture = new HashMap<>();
    private final List<MaterialRegion> materials = new ArrayList<>();

    /** Decoded textures, so each is read from disk once. Never exported. */
    private final Map<Identifier, BlockTexture> textures = new HashMap<>();

    /**
     * @param detail how finely a face may follow its texture; 1 gives each face
     *               a single colour
     */
    public ModelCapture(Minecraft client, int detail) {
        this.client = client;
        this.blockColors = client.getBlockColors();
        this.detail = Math.max(1, detail);
    }

    /** All materials met so far, in the order their index refers to. */
    public List<MaterialRegion> materials() {
        return List.copyOf(materials);
    }

    /**
     * Collects the faces of a block state.
     *
     * <p>Returns an empty list for anything without a model, which includes air
     * and blocks drawn by a block entity renderer rather than a model. Those are
     * left to the bounding box in {@code shapes.json}.
     *
     * @param level    the world the block stands in, for the biome tint
     * @param position where it stands, for the same reason
     */
    public List<ModelQuad> quadsOf(BlockState state, ClientLevel level, BlockPos position) {
        if (state.isAir()) {
            return List.of();
        }

        // The caller walks the selection with one shared mutable cursor, so take
        // a copy before handing the position on to anything else.
        BlockPos where = position.immutable();

        try {
            BlockStateModel model = client.getModelManager().getBlockStateModelSet().get(state);
            List<BlockStateModelPart> parts = new ArrayList<>();
            random.setSeed(VARIANT_SEED);
            model.collectParts(random, parts);

            List<ModelQuad> quads = new ArrayList<>();
            for (BlockStateModelPart part : parts) {
                // Quads tied to a face, plus the ones that belong to no face.
                for (Direction direction : Direction.values()) {
                    collect(quads, part.getQuads(direction), direction.getName(), state, level, where);
                }
                collect(quads, part.getQuads(null), null, state, level, where);
            }

            // A few blocks are drawn half by a model and half by the game: a
            // bell is a frame plus a bell, a copper golem statue is nothing but
            // the second half. That half is drawn by a block entity renderer and
            // has to be asked for separately, or it is simply absent -- and a
            // block with a partial model gets no bounding box to fall back on
            // either.
            collect(quads, BlockEntityCapture.facesOf(client, level, where));
            return quads;
        } catch (RuntimeException e) {
            // A modded block may not survive being asked outside a render pass.
            // One awkward block falls back to its bounding box rather than
            // failing the export.
            VoxelPrint.LOGGER.warn("Could not read the model of {}", state, e);
            return List.of();
        }
    }

    private void collect(List<ModelQuad> into, List<BakedQuad> quads, String direction,
                         BlockState state, ClientLevel level, BlockPos position) {
        for (BakedQuad quad : quads) {
            TextureAtlasSprite sprite = quad.materialInfo().sprite();
            Identifier name = sprite.contents().name();
            BlockTexture texture = textureOf(name, sprite);
            int tint = tintOf(quad, state, level, position);

            float[] positions = corners(quad);
            float[] texels = texture == null ? new float[8] : texels(quad, sprite, texture);
            add(into, name, texture, tint, direction, positions, texels);
        }
    }

    /**
     * Collects the faces a block entity renderer draws.
     *
     * <p>They arrive already placed in the block, and with their texture
     * coordinates running from zero to one rather than in pixels -- an entity
     * model is drawn across its whole texture, whether that texture stands on
     * its own or was stitched into an atlas. Scaling them up is all that
     * separates the two paths; from there a face is a face.
     *
     * <p>Never tinted. A tint comes from the biome by way of the block colour
     * handlers, and nothing that is drawn this way takes one.
     */
    private void collect(List<ModelQuad> into, List<BlockEntityCapture.Face> faces) {
        for (BlockEntityCapture.Face face : faces) {
            BlockTexture texture = textureOf(face.texture(), face.sprite());
            float[] texels = new float[8];
            if (texture != null) {
                float[] source = face.texels();
                for (int corner = 0; corner < 4; corner++) {
                    texels[corner * 2] = source[corner * 2] * texture.width();
                    texels[corner * 2 + 1] = source[corner * 2 + 1] * texture.height();
                }
            }
            add(into, face.texture(), texture, MaterialRegion.NO_TINT, null, face.positions(), texels);
        }
    }

    /**
     * Cuts a face along its texture and records what each piece is made of.
     *
     * <p>The one place a face becomes quads, whichever of the two ways it was
     * read, so both are split and folded into materials by the same rules.
     */
    private void add(List<ModelQuad> into, Identifier name, BlockTexture texture, int tint,
                     String direction, float[] positions, float[] texels) {
        for (QuadSplitter.Patch patch : QuadSplitter.split(positions, texels, texture, detail)) {
            // A patch with no colour is the splitter saying it could not
            // measure one, not that the face is invisible: those it drops
            // itself. The whole texture is the best remaining answer.
            int colour = patch.colour() == BlockTexture.TRANSPARENT
                    ? wholeTexture(texture)
                    : patch.colour();
            into.add(new ModelQuad(
                    materialFor(name, tint, tinted(colour, tint)), direction, patch.vertices()));
        }
    }

    private static float[] corners(BakedQuad quad) {
        float[] vertices = new float[12];
        for (int corner = 0; corner < 4; corner++) {
            Vector3fc position = quad.position(corner);
            vertices[corner * 3] = position.x();
            vertices[corner * 3 + 1] = position.y();
            vertices[corner * 3 + 2] = position.z();
        }
        return vertices;
    }

    /**
     * Where each corner of a face sits on its texture, in pixels.
     *
     * <p>The model stores coordinates into the stitched atlas, which says
     * nothing on its own: the same numbers mean a different texture depending
     * on where it happened to be stitched. Subtracting the sprite's own corner
     * and scaling by its size turns them back into pixels of that one texture,
     * which is what a colour can be measured from.
     */
    private static float[] texels(BakedQuad quad, TextureAtlasSprite sprite, BlockTexture texture) {
        float spanU = sprite.getU1() - sprite.getU0();
        float spanV = sprite.getV1() - sprite.getV0();
        float[] texels = new float[8];

        for (int corner = 0; corner < 4; corner++) {
            long packed = quad.packedUV(corner);
            float u = UVPair.unpackU(packed);
            float v = UVPair.unpackV(packed);
            texels[corner * 2] = spanU == 0.0F ? 0.0F
                    : (u - sprite.getU0()) / spanU * texture.width();
            texels[corner * 2 + 1] = spanV == 0.0F ? 0.0F
                    : (v - sprite.getV0()) / spanV * texture.height();
        }
        return texels;
    }

    private int wholeTexture(BlockTexture texture) {
        if (texture == null) {
            return UNKNOWN_COLOUR;
        }
        int colour = texture.average(0, 0, texture.width(), texture.height());
        return colour == BlockTexture.TRANSPARENT ? UNKNOWN_COLOUR : colour;
    }

    /**
     * Finds or records the material for a piece of a face.
     *
     * <p>Two faces of one texture rarely average to the same number: a stair
     * shows a different part of the planks than a full block does, and comes
     * out a shade off. Kept apart, a single village turns into hundreds of
     * browns nobody could tell apart or print differently, so a colour close
     * enough to one already recorded joins it instead.
     *
     * <p>New colours are only ever compared against the ones already kept, not
     * against each other, so every colour stays within one step of the material
     * it was folded into rather than drifting along a chain of near matches.
     */
    private int materialFor(Identifier texture, int tint, int colour) {
        MaterialKey key = new MaterialKey(texture, tint, colour);
        Integer known = materialIndices.get(key);
        if (known != null) {
            return known;
        }

        List<Integer> sameTexture = byTexture.computeIfAbsent(texture, unused -> new ArrayList<>());
        for (int candidate : sameTexture) {
            MaterialRegion material = materials.get(candidate);
            if (material.tint() == tint && QuadSplitter.alike(material.colour(), colour)) {
                materialIndices.put(key, candidate);
                return candidate;
            }
        }

        int index = materials.size();
        materialIndices.put(key, index);
        sameTexture.add(index);
        materials.add(new MaterialRegion(texture.toString(), tint, colour));
        return index;
    }

    /**
     * Reads the tint Minecraft draws this face with.
     *
     * <p>Grass, leaves, vines, water and redstone ship a grey or plain texture
     * and take their colour from the biome when they are drawn. Exporting the
     * texture on its own gives a lawn in concrete grey, which is neither what
     * anybody sees nor what should be printed.
     *
     * <p>Read at the block's own position, so a swamp and a plain give the two
     * different greens they actually have.
     *
     * @return the tint as 0xRRGGBB, or {@link MaterialRegion#NO_TINT}
     */
    private int tintOf(BakedQuad quad, BlockState state, ClientLevel level, BlockPos position) {
        int tintIndex = quad.materialInfo().tintIndex();
        if (tintIndex < 0) {
            return MaterialRegion.NO_TINT;
        }

        BlockTintSource source = blockColors.getTintSource(state, tintIndex);
        if (source == null) {
            return MaterialRegion.NO_TINT;
        }
        return source.colorInWorld(state, level, position) & 0xffffff;
    }

    /**
     * Applies a tint the way the renderer does: one channel times the other.
     *
     * <p>A tint of white leaves the texture alone, which is why an untinted
     * material needs no multiplication at all.
     */
    private static int tinted(int colour, int tint) {
        if (tint == MaterialRegion.NO_TINT) {
            return colour;
        }
        int red = ((colour >> 16) & 0xff) * ((tint >> 16) & 0xff) / 0xff;
        int green = ((colour >> 8) & 0xff) * ((tint >> 8) & 0xff) / 0xff;
        int blue = (colour & 0xff) * (tint & 0xff) / 0xff;
        return red << 16 | green << 8 | blue;
    }

    /**
     * Reads a texture, once.
     *
     * <p>Read from the resource manager rather than the texture atlas: the
     * atlas has no public way to get at its pixels, and going through the
     * resource manager works for resource packs and modded textures alike.
     *
     * @param sprite the atlas sprite it was stitched into, or null for a
     *               texture that stands on its own, as an entity model's does
     * @return the texture, or null when it could not be read
     */
    private BlockTexture textureOf(Identifier name, TextureAtlasSprite sprite) {
        if (textures.containsKey(name)) {
            return textures.get(name);
        }

        BlockTexture texture = readTexture(name, sprite);
        textures.put(name, texture);
        return texture;
    }

    private BlockTexture readTexture(Identifier name, TextureAtlasSprite sprite) {
        Identifier file = Identifier.fromNamespaceAndPath(
                name.getNamespace(), "textures/" + name.getPath() + ".png");

        List<Resource> stack = client.getResourceManager().getResourceStack(file);
        if (stack.isEmpty()) {
            VoxelPrint.LOGGER.warn("No texture found for {}", name);
            return null;
        }

        // The last entry is the highest priority pack, which is what the player
        // actually sees.
        Resource resource = stack.get(stack.size() - 1);
        try (InputStream in = resource.open(); NativeImage image = NativeImage.read(in)) {
            // An animated texture is one frame above the next in a single file.
            // The sprite knows how tall one frame is; only the first is read,
            // which is the one a block shows at rest. Without a sprite there is
            // no atlas and no animation to undo, so the file is the texture.
            int width = sprite == null ? image.getWidth()
                    : Math.min(image.getWidth(), Math.max(1, sprite.contents().width()));
            int height = sprite == null ? image.getHeight()
                    : Math.min(image.getHeight(), Math.max(1, sprite.contents().height()));

            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y * width + x] = image.getPixel(x, y);
                }
            }
            return new BlockTexture(width, height, pixels);
        } catch (IOException | RuntimeException e) {
            VoxelPrint.LOGGER.warn("Could not read the texture {}", file, e);
            return null;
        }
    }
}
