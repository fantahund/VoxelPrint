package de.tobi.voxelprint.schematic;

import de.tobi.voxelprint.export.ExportSnapshot;
import de.tobi.voxelprint.export.PaletteEntry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

/**
 * Writes a Sponge Schematic Version 3 file.
 *
 * <p>The structure below was taken from the reference implementation, WorldEdit's
 * own {@code SpongeSchematicV3Writer}, not from the specification document alone.
 * That document lists "Rename Palette to BlockPalette" among the version 3
 * changes while still calling the tag {@code Palette} in its schema -- it
 * contradicts itself. WorldEdit writes {@code Palette}, and WorldEdit is what has
 * to read our files, so {@code Palette} it is.
 *
 * <p>Layout:
 *
 * <pre>
 * root (unnamed compound)
 *  +-- Schematic
 *       +-- Version      int, always 3
 *       +-- DataVersion  int, the Minecraft data version
 *       +-- Metadata     compound, only Date here
 *       +-- Width        short, unsigned
 *       +-- Height       short, unsigned
 *       +-- Length       short, unsigned
 *       +-- Offset       int[3]
 *       +-- Blocks
 *            +-- Palette compound, block state -> index
 *            +-- Data    byte[], the indices as concatenated VarInts
 * </pre>
 *
 * <p>No block entities and no entities: both are explicitly out of scope, and
 * both tags are optional, so leaving them out produces a valid file.
 */
public final class SpongeSchematicWriter implements SchematicWriter {

    private static final int SCHEMATIC_VERSION = 3;

    /** Width, Height and Length are unsigned shorts in this format. */
    private static final int MAX_EDGE = 65535;

    @Override
    public String formatId() {
        return "sponge-schematic-v3";
    }

    @Override
    public String fileName() {
        return "structure.schem";
    }

    @Override
    public void write(ExportSnapshot snapshot, OutputStream out) throws IOException {
        requireFits(snapshot.width(), "Width");
        requireFits(snapshot.height(), "Height");
        requireFits(snapshot.depth(), "Length");

        CompoundTag schematic = new CompoundTag();
        schematic.putInt("Version", SCHEMATIC_VERSION);
        schematic.putInt("DataVersion", snapshot.dataVersion());
        schematic.put("Metadata", metadata(snapshot));
        schematic.putShort("Width", (short) snapshot.width());
        schematic.putShort("Height", (short) snapshot.height());
        schematic.putShort("Length", (short) snapshot.depth());

        // WorldEdit writes min minus origin here. For us the minimum corner of
        // the selection is the origin, so the offset is zero and pasting puts
        // the structure exactly where the player points.
        schematic.putIntArray("Offset", new int[] {0, 0, 0});

        schematic.put("Blocks", blocks(snapshot));

        CompoundTag root = new CompoundTag();
        root.put("Schematic", schematic);

        // Sponge schematics are GZip compressed NBT with an unnamed root.
        NbtIo.writeCompressed(root, out);
    }

    private static CompoundTag metadata(ExportSnapshot snapshot) {
        CompoundTag metadata = new CompoundTag();
        metadata.putLong("Date", snapshot.capturedAt().toEpochMilli());
        // Name and Author stay unset on purpose: Author would be a player name,
        // and exported files should not carry one.
        return metadata;
    }

    private static CompoundTag blocks(ExportSnapshot snapshot) {
        CompoundTag palette = new CompoundTag();
        for (int index = 0; index < snapshot.palette().size(); index++) {
            PaletteEntry entry = snapshot.palette().get(index);
            palette.putInt(entry.blockState(), index);
        }

        CompoundTag blocks = new CompoundTag();
        blocks.put("Palette", palette);
        blocks.putByteArray("Data", encodeIndices(snapshot));
        return blocks;
    }

    /**
     * Packs the palette indices as concatenated VarInts.
     *
     * <p>The snapshot already stores blocks in the order this format expects,
     * {@code x + z * width + y * width * depth}, so a straight walk over the
     * array is exactly the sequence the format wants.
     */
    private static byte[] encodeIndices(ExportSnapshot snapshot) {
        int volume = snapshot.volume();
        // Most palettes are far below 128 entries, so one byte per block is the
        // normal case and this initial size usually avoids any regrowth.
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(volume);
        for (int index = 0; index < volume; index++) {
            writeVarInt(buffer, snapshot.paletteIndexAt(index));
        }
        return buffer.toByteArray();
    }

    /** Standard Minecraft VarInt encoding, as used by the reference writer. */
    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & -128) != 0) {
            out.write((value & 127) | 128);
            value >>>= 7;
        }
        out.write(value);
    }

    private static void requireFits(int edge, String name) {
        if (edge > MAX_EDGE) {
            throw new IllegalArgumentException(
                    name + " of " + edge + " does not fit into an unsigned short");
        }
    }
}
