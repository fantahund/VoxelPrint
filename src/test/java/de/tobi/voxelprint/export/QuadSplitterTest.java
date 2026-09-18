package de.tobi.voxelprint.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for cutting a face up to follow its texture.
 *
 * <p>The face under test is the unit square in the XY plane, wound the way a
 * baked quad is, with its texture laid over it the way Minecraft does: the
 * texture's first row is at the top of the face, because texture rows count
 * downwards and block coordinates count upwards. Getting that the wrong way
 * round would put a torch's head at its foot, which is exactly the kind of
 * mistake worth a test.
 */
class QuadSplitterTest {

    private static final int OPAQUE = 0xff000000;
    private static final int BROWN = 0x8a6a3a;
    private static final int RED = 0xd04020;

    /** Corners of the unit square: bottom left, bottom right, top right, top left. */
    private static final float[] FACE = {
        0, 0, 0,
        1, 0, 0,
        1, 1, 0,
        0, 1, 0,
    };

    /** The same corners in texture pixels. Row 0 of the texture is the top. */
    private static final float[] TEXELS = {
        0, 16,
        16, 16,
        16, 0,
        0, 0,
    };

    private static BlockTexture texture(java.util.function.IntBinaryOperator colourAt) {
        int[] pixels = new int[16 * 16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                pixels[y * 16 + x] = colourAt.applyAsInt(x, y);
            }
        }
        return new BlockTexture(16, 16, pixels);
    }

    private static float minY(QuadSplitter.Patch patch) {
        float min = Float.MAX_VALUE;
        for (int corner = 0; corner < 4; corner++) {
            min = Math.min(min, patch.vertices()[corner * 3 + 1]);
        }
        return min;
    }

    private static float maxY(QuadSplitter.Patch patch) {
        float max = -Float.MAX_VALUE;
        for (int corner = 0; corner < 4; corner++) {
            max = Math.max(max, patch.vertices()[corner * 3 + 1]);
        }
        return max;
    }

    @Test
    @DisplayName("A texture of one colour stays one face")
    void uniformTextureIsNotSplit() {
        BlockTexture plain = texture((x, y) -> OPAQUE | BROWN);

        List<QuadSplitter.Patch> patches = QuadSplitter.split(FACE, TEXELS, plain, 4);

        assertEquals(1, patches.size(), "a plain texture should not be cut up");
        assertEquals(BROWN, patches.get(0).colour());
        assertArrayEqualsRoughly(FACE, patches.get(0).vertices());
    }

    @Test
    @DisplayName("Noise within a texture does not split it either")
    void noisyTextureIsNotSplit() {
        // A stone texture is never one exact value, but it is one colour.
        BlockTexture stone = texture((x, y) -> OPAQUE | (0x7a7a7a + ((x + y) % 3) * 0x040404));

        assertEquals(1, QuadSplitter.split(FACE, TEXELS, stone, 4).size());
    }

    @Test
    @DisplayName("A lit head on top becomes its own face, at the top")
    void brightBandBecomesItsOwnFace() {
        // The top quarter of the texture is the flame, the rest is the stick.
        BlockTexture torch = texture((x, y) -> OPAQUE | (y < 4 ? RED : BROWN));

        List<QuadSplitter.Patch> patches = QuadSplitter.split(FACE, TEXELS, torch, 4);
        patches = patches.stream().sorted(Comparator.comparing(QuadSplitterTest::minY)).toList();

        assertEquals(2, patches.size(), "stick and head");

        QuadSplitter.Patch stick = patches.get(0);
        assertEquals(BROWN, stick.colour());
        assertEquals(0.0F, minY(stick), 1e-5);
        assertEquals(0.75F, maxY(stick), 1e-5);

        QuadSplitter.Patch head = patches.get(1);
        assertEquals(RED, head.colour(), "the flame belongs to the top of the face");
        assertEquals(0.75F, minY(head), 1e-5);
        assertEquals(1.0F, maxY(head), 1e-5);
    }

    @Test
    @DisplayName("Detail of one keeps every face whole")
    void detailOfOneKeepsTheFaceWhole() {
        BlockTexture torch = texture((x, y) -> OPAQUE | (y < 4 ? RED : BROWN));

        List<QuadSplitter.Patch> patches = QuadSplitter.split(FACE, TEXELS, torch, 1);

        assertEquals(1, patches.size());
        assertArrayEqualsRoughly(FACE, patches.get(0).vertices());
    }

    @Test
    @DisplayName("Parts the texture shows nothing at are left out")
    void transparentPartsAreDropped() {
        // Only the right half of the texture is drawn on.
        BlockTexture half = texture((x, y) -> x < 8 ? 0 : OPAQUE | BROWN);

        List<QuadSplitter.Patch> patches = QuadSplitter.split(FACE, TEXELS, half, 4);

        assertEquals(1, patches.size());
        float minX = Float.MAX_VALUE;
        for (int corner = 0; corner < 4; corner++) {
            minX = Math.min(minX, patches.get(0).vertices()[corner * 3]);
        }
        assertEquals(0.5F, minX, 1e-5, "the empty half should not be printed");
    }

    @Test
    @DisplayName("A fully transparent texture keeps the face rather than losing it")
    void fullyTransparentTextureKeepsTheFace() {
        BlockTexture nothing = texture((x, y) -> 0);

        List<QuadSplitter.Patch> patches = QuadSplitter.split(FACE, TEXELS, nothing, 4);

        assertEquals(1, patches.size());
        assertEquals(BlockTexture.TRANSPARENT, patches.get(0).colour(),
                "the caller is told no colour could be measured");
        assertArrayEqualsRoughly(FACE, patches.get(0).vertices());
    }

    @Test
    @DisplayName("A missing texture leaves the face alone")
    void missingTextureLeavesTheFaceAlone() {
        List<QuadSplitter.Patch> patches = QuadSplitter.split(FACE, TEXELS, null, 4);

        assertEquals(1, patches.size());
        assertEquals(BlockTexture.TRANSPARENT, patches.get(0).colour());
        assertArrayEqualsRoughly(FACE, patches.get(0).vertices());
    }

    @Test
    @DisplayName("A face showing only a few pixels is left whole")
    void aSmallCropIsNotCutUp() {
        // Every pixel a different colour. Cut into single pixels this looks
        // like a great deal of detail; it is a stair showing a corner of a
        // noisy stone texture, and averaging is the only honest answer.
        BlockTexture noise = texture((x, y) -> OPAQUE | (0x707070 + ((x * 7 + y * 13) % 5) * 0x0a0a0a));
        // Four pixels to a side, which is one cell's worth.
        float[] texels = {0, 4, 4, 4, 4, 0, 0, 0};

        List<QuadSplitter.Patch> patches = QuadSplitter.split(FACE, texels, noise, 4);

        assertEquals(1, patches.size(), "a four pixel crop has one cell in it");
        assertArrayEqualsRoughly(FACE, patches.get(0).vertices());
    }

    @Test
    @DisplayName("Noise is not mistaken for detail at any size a face is shown at")
    void noiseIsNeverSplit() {
        BlockTexture stone = texture((x, y) -> OPAQUE | (0x707070 + ((x * 7 + y * 13) % 5) * 0x0a0a0a));

        // A full face, a half one and a quarter one, as stairs and slabs show.
        for (int pixels : new int[] {16, 8, 4}) {
            float[] texels = {0, pixels, pixels, pixels, pixels, 0, 0, 0};
            assertEquals(1, QuadSplitter.split(FACE, texels, stone, 4).size(),
                    "a noisy texture shown across " + pixels + " pixels is still one colour");
        }
    }

    @Test
    @DisplayName("Real detail survives the same rule")
    void realDetailIsStillSplit() {
        BlockTexture torch = texture((x, y) -> OPAQUE | (y < 4 ? RED : BROWN));

        assertTrue(QuadSplitter.split(FACE, TEXELS, torch, 4).size() > 1,
                "a flame above a stick is two colours, not noise");
    }

    private static void assertArrayEqualsRoughly(float[] expected, float[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], 1e-5, "coordinate " + i);
        }
    }
}
