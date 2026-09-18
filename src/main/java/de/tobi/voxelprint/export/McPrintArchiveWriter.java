package de.tobi.voxelprint.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.tobi.voxelprint.format.BlockModels;
import de.tobi.voxelprint.format.BlockShapes;
import de.tobi.voxelprint.format.BlockSummary;
import de.tobi.voxelprint.format.Manifest;
import de.tobi.voxelprint.format.McPrintFormat;
import de.tobi.voxelprint.schematic.SchematicWriter;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a {@code .mcprint} archive.
 *
 * <p>Knows nothing about Minecraft and nothing about files -- it turns a
 * snapshot into bytes on a stream. That keeps it usable from a background
 * thread and testable without a running game.
 */
public final class McPrintArchiveWriter {

    /**
     * HTML escaping is off on purpose.
     *
     * <p>With it on, Gson escapes the equals sign in a block state such as
     * {@code minecraft:oak_log[axis=y]} into a unicode escape. That parses back
     * correctly, but the structure file is written through a plain
     * {@code JsonWriter} which does not escape, so the same block state would
     * look different in two entries of the same archive.
     *
     * <p>The escape sequence is described rather than written out here: Java
     * resolves unicode escapes even inside comments.
     */
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final SchematicWriter structureWriter;
    private final String modVersion;
    private final boolean includeAir;
    private final boolean createBlockSummary;
    private final boolean includeShapes;
    private final boolean includeModels;

    public McPrintArchiveWriter(SchematicWriter structureWriter,
                                String modVersion,
                                boolean includeAir,
                                boolean createBlockSummary,
                                boolean includeShapes,
                                boolean includeModels) {
        this.structureWriter = Objects.requireNonNull(structureWriter, "structureWriter");
        this.modVersion = Objects.requireNonNull(modVersion, "modVersion");
        this.includeAir = includeAir;
        this.createBlockSummary = createBlockSummary;
        this.includeShapes = includeShapes;
        this.includeModels = includeModels;
    }

    public void write(ExportSnapshot snapshot, OutputStream out) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            Manifest manifest = Manifest.of(snapshot, modVersion, includeAir,
                    structureWriter.formatId(), structureWriter.fileName(),
                    includeShapes ? McPrintFormat.ENTRY_SHAPES : null,
                    includeModels ? McPrintFormat.ENTRY_MODELS : null);
            writeJson(zip, McPrintFormat.ENTRY_MANIFEST, manifest);

            zip.putNextEntry(new ZipEntry(structureWriter.fileName()));
            structureWriter.write(snapshot, new NonClosingOutputStream(zip));
            zip.closeEntry();

            if (includeShapes) {
                writeJson(zip, McPrintFormat.ENTRY_SHAPES, BlockShapes.of(snapshot));
            }

            if (includeModels) {
                writeJson(zip, McPrintFormat.ENTRY_MODELS, BlockModels.of(snapshot));
            }

            if (createBlockSummary) {
                writeJson(zip, McPrintFormat.ENTRY_BLOCK_SUMMARY, BlockSummary.of(snapshot, includeAir));
            }
        }
    }

    /**
     * A view of the archive stream that cannot be closed.
     *
     * <p>{@code NbtIo.writeCompressed} closes the stream it is handed, which
     * would close the entire ZIP instead of just the current entry. Relying on
     * every present and future writer to avoid that is a trap, so the archive
     * protects itself: closing this view only flushes.
     */
    private static final class NonClosingOutputStream extends FilterOutputStream {

        NonClosingOutputStream(OutputStream delegate) {
            super(delegate);
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            // FilterOutputStream would forward this one byte at a time, which
            // matters a great deal for a structure of a million blocks.
            out.write(buffer, offset, length);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }

    private static void writeJson(ZipOutputStream zip, String entryName, Object value) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        // Not closed on purpose: closing the writer would close the whole zip
        // stream, not just this entry.
        Writer writer = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
        GSON.toJson(value, writer);
        writer.flush();
        zip.closeEntry();
    }
}
