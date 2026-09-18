package de.tobi.voxelprint.schematic;

import com.google.gson.stream.JsonWriter;
import de.tobi.voxelprint.export.ExportSnapshot;
import de.tobi.voxelprint.export.PaletteEntry;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * VoxelPrint's own structure format, {@code internal-v1}.
 *
 * <p>Deliberately boring so that a website can read it with nothing but a JSON
 * parser. The layout is:
 *
 * <pre>{@code
 * {
 *   "formatVersion": 1,
 *   "size": { "width": 64, "height": 32, "depth": 64 },
 *   "palette": ["minecraft:air", "minecraft:stone", ...],
 *   "blocks": [0, 1, 1, 0, ...]
 * }
 * }</pre>
 *
 * <p>{@code blocks} holds one palette index per block, ordered
 * {@code x + z * width + y * width * depth} -- the same order the Sponge
 * Schematic format uses, so the later writer can reuse the array unchanged.
 *
 * <p>Written with a streaming {@link JsonWriter}: a selection of a million
 * blocks would otherwise mean a million boxed Integers in an intermediate list.
 */
public final class InternalStructureWriter implements SchematicWriter {

    private static final int FORMAT_VERSION = 1;

    @Override
    public String formatId() {
        return "internal-v1";
    }

    @Override
    public String fileName() {
        return "structure.json";
    }

    @Override
    public void write(ExportSnapshot snapshot, OutputStream out) throws IOException {
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        JsonWriter json = new JsonWriter(writer);
        json.setIndent("  ");

        json.beginObject();
        json.name("formatVersion").value(FORMAT_VERSION);

        json.name("size").beginObject();
        json.name("width").value(snapshot.width());
        json.name("height").value(snapshot.height());
        json.name("depth").value(snapshot.depth());
        json.endObject();

        json.name("order").value("x + z * width + y * width * depth");

        json.name("palette").beginArray();
        for (PaletteEntry entry : snapshot.palette()) {
            json.value(entry.blockState());
        }
        json.endArray();

        json.name("blocks").beginArray();
        int volume = snapshot.volume();
        for (int index = 0; index < volume; index++) {
            json.value(snapshot.paletteIndexAt(index));
        }
        json.endArray();

        json.endObject();
        // Flush rather than close: the caller owns the stream, and closing it
        // here would end the ZIP entry too early.
        json.flush();
        writer.flush();
    }
}
