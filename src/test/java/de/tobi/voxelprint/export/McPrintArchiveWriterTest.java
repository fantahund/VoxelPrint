package de.tobi.voxelprint.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tobi.voxelprint.schematic.SchematicWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the archive as a whole rather than each writer on its own.
 *
 * <p>That distinction is not academic: the schematic writer was correct in
 * isolation and still broke the export, because the NBT library closed the
 * stream it was handed and took the surrounding ZIP with it. Only a test that
 * writes a complete archive catches that.
 */
class McPrintArchiveWriterTest {

    /** A structure writer that closes its output, like the real NBT one does. */
    private static final class ClosingStructureWriter implements SchematicWriter {

        @Override
        public String formatId() {
            return "test-closing";
        }

        @Override
        public String fileName() {
            return "structure.test";
        }

        @Override
        public void write(ExportSnapshot snapshot, OutputStream out) throws IOException {
            out.write("structure".getBytes(StandardCharsets.UTF_8));
            out.close();
        }
    }

    /** A structure writer that politely leaves the stream alone. */
    private static final class PoliteStructureWriter implements SchematicWriter {

        @Override
        public String formatId() {
            return "test-polite";
        }

        @Override
        public String fileName() {
            return "structure.test";
        }

        @Override
        public void write(ExportSnapshot snapshot, OutputStream out) throws IOException {
            out.write("structure".getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    @Test
    @DisplayName("a structure writer that closes the stream must not break the archive")
    void survivesAClosingStructureWriter() throws IOException {
        Map<String, String> entries = writeArchive(new ClosingStructureWriter(), true);

        assertEquals(
                List.of("manifest.json", "structure.test", "block-summary.json"),
                List.copyOf(entries.keySet()),
                "all three entries must be present, in order");
        assertEquals("structure", entries.get("structure.test"));
    }

    @Test
    @DisplayName("a well behaved structure writer produces the same archive")
    void behavesTheSameForAPoliteWriter() throws IOException {
        assertEquals(
                writeArchive(new ClosingStructureWriter(), true).keySet(),
                writeArchive(new PoliteStructureWriter(), true).keySet());
    }

    @Test
    @DisplayName("the block summary can be switched off")
    void omitsTheSummaryWhenDisabled() throws IOException {
        Map<String, String> entries = writeArchive(new PoliteStructureWriter(), false);
        assertFalse(entries.containsKey("block-summary.json"));
        assertTrue(entries.containsKey("manifest.json"));
    }

    @Test
    @DisplayName("the manifest names the format and file of the chosen writer")
    void manifestFollowsTheWriter() throws IOException {
        String manifest = writeArchive(new PoliteStructureWriter(), true).get("manifest.json");
        assertTrue(manifest.contains("\"structureFormat\": \"test-polite\""), manifest);
        assertTrue(manifest.contains("\"structureFile\": \"structure.test\""), manifest);
        assertTrue(manifest.contains("\"volume\": 8"), manifest);
        assertTrue(manifest.contains("\"nonAirBlockCount\": 6"), manifest);
        assertTrue(manifest.contains("\"paletteSize\": 3"), manifest);
    }

    @Test
    @DisplayName("air is left out of the summary and states are sorted by count")
    void summaryExcludesAirAndSortsByCount() throws IOException {
        String summary = writeArchive(new PoliteStructureWriter(), true).get("block-summary.json");

        assertFalse(summary.contains("minecraft:air"), "air must not be listed when includeAir is false");

        int stone = summary.indexOf("minecraft:stone");
        int stairs = summary.indexOf("minecraft:oak_stairs");
        assertTrue(stone >= 0 && stairs >= 0, summary);
        assertTrue(stone < stairs, "stone occurs more often and must be listed first");

        // Written without unicode escapes, so the same state reads identically
        // here and in the structure file.
        assertTrue(summary.contains("minecraft:oak_stairs[facing=north]"), summary);
    }

    @Test
    @DisplayName("shapes are written and line up with the palette")
    void writesShapes() throws IOException {
        Map<String, String> entries = writeArchive(new PoliteStructureWriter(), true, true);

        assertTrue(entries.containsKey("shapes.json"), entries.keySet().toString());
        String shapes = entries.get("shapes.json");

        // Three palette entries, so three shape entries in the same order:
        // nothing for air, one box for stone, two for the stair.
        assertTrue(shapes.contains("\"formatVersion\": 1"), shapes);
        int firstBox = shapes.indexOf("0.5");
        assertTrue(firstBox > 0, "the stair shape should carry a half height box: " + shapes);

        String manifest = entries.get("manifest.json");
        assertTrue(manifest.contains("\"shapesFile\": \"shapes.json\""), manifest);
    }

    @Test
    @DisplayName("without shapes the manifest does not mention a shape file")
    void omitsShapes() throws IOException {
        Map<String, String> entries = writeArchive(new PoliteStructureWriter(), true, false);

        assertFalse(entries.containsKey("shapes.json"));
        // Absent rather than null or empty, so an export without shapes looks
        // exactly like one made before shapes existed.
        assertFalse(entries.get("manifest.json").contains("shapesFile"));
    }

    /**
     * Builds a 2x2x2 snapshot: two air blocks, four stone, two stairs.
     */
    private static ExportSnapshot snapshot() {
        List<PaletteEntry> palette = List.of(
                new PaletteEntry("minecraft:air", "minecraft:air", true),
                new PaletteEntry("minecraft:stone", "minecraft:stone", false),
                new PaletteEntry("minecraft:oak_stairs", "minecraft:oak_stairs[facing=north]", false));

        int[] blocks = {0, 0, 1, 1, 1, 1, 2, 2};
        long[] counts = {2, 4, 2};

        // Air has no shape, stone is a full cube, the stair is two boxes.
        List<List<BlockBox>> shapes = List.of(
                List.of(),
                List.of(BlockBox.FULL),
                List.of(
                        new BlockBox(0.0, 0.0, 0.0, 1.0, 0.5, 1.0),
                        new BlockBox(0.0, 0.5, 0.5, 1.0, 1.0, 1.0)));

        return new ExportSnapshot("minecraft:overworld", 0, 64, 0, 2, 2, 2,
                palette, shapes, List.of(List.of(), List.of(), List.of()), List.of(),
                blocks, counts, 6, "26.3", 5023,
                Instant.parse("2026-09-17T10:00:00Z"));
    }

    /** Writes an archive and returns its entries in order, as text. */
    private static Map<String, String> writeArchive(SchematicWriter structureWriter, boolean withSummary)
            throws IOException {
        return writeArchive(structureWriter, withSummary, false);
    }

    private static Map<String, String> writeArchive(
            SchematicWriter structureWriter, boolean withSummary, boolean withShapes) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new McPrintArchiveWriter(structureWriter, "26.3-0.1.0", false, withSummary, withShapes, false)
                .write(snapshot(), bytes);

        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
