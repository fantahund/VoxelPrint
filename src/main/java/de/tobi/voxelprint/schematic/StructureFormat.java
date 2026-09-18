package de.tobi.voxelprint.schematic;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Which structure file an export puts into the archive.
 *
 * <p>Both formats carry exactly the same blocks. They differ in who can read
 * them comfortably: {@code .schem} is what Minecraft tooling such as WorldEdit
 * and Litematica expects, while the JSON variant needs nothing but a JSON parser
 * and is useful anywhere an NBT reader would be a nuisance.
 */
public enum StructureFormat {

    /** Sponge Schematic Version 3, the interchange format for Minecraft tools. */
    SPONGE_V3("sponge-schematic-v3", SpongeSchematicWriter::new),

    /** VoxelPrint's plain JSON structure. */
    INTERNAL_JSON("internal-v1", InternalStructureWriter::new);

    private final String id;
    private final Supplier<SchematicWriter> factory;

    StructureFormat(String id, Supplier<SchematicWriter> factory) {
        this.id = id;
        this.factory = factory;
    }

    /** Stable id, also what ends up in the configuration file. */
    public String id() {
        return id;
    }

    public SchematicWriter createWriter() {
        return factory.get();
    }

    /** Resolves a stored id, falling back to the default for anything unknown. */
    public static StructureFormat fromId(String id, StructureFormat fallback) {
        if (id != null) {
            String normalized = id.trim().toLowerCase(Locale.ROOT);
            for (StructureFormat format : values()) {
                if (format.id.equals(normalized)) {
                    return format;
                }
            }
        }
        return fallback;
    }
}
