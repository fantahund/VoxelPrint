package de.tobi.voxelprint.format;

import de.tobi.voxelprint.export.ExportSnapshot;
import de.tobi.voxelprint.util.TimeUtil;

/**
 * Contents of {@code manifest.json}, the one entry every {@code .mcprint} file has.
 *
 * <p>Carries no player name, no world path and no local time zone, so a file can
 * be shared without leaking anything about who made it.
 *
 * <p>Note on counts: {@code paletteSize} is the size of the palette in the
 * structure file and therefore always includes air. The block summary reports
 * {@code uniqueBlockStates}, which is how many states it actually lists and does
 * depend on {@code includeAir}. Two different questions, two different names.
 */
public record Manifest(
        String format,
        int formatVersion,
        String createdAt,
        Generator generator,
        String minecraftVersion,
        int dataVersion,
        String dimension,
        Origin origin,
        Size size,
        int volume,
        long nonAirBlockCount,
        int paletteSize,
        boolean includeAir,
        boolean blockEntitiesIncluded,
        boolean entitiesIncluded,
        String structureFormat,
        String structureFile,
        /**
         * Name of the shape file, or null when shapes were not exported.
         *
         * <p>Null rather than an empty string: a reader checking for the field
         * gets a clear yes or no, and Gson leaves it out of the document
         * entirely, so an export without shapes looks exactly like one made
         * before shapes existed.
         */
        String shapesFile,
        /** Name of the model file, or null when models were not exported. */
        String modelsFile) {

    /** What produced the file. */
    public record Generator(String name, String version, String modLoader) {
    }

    /** Minimum corner of the selection in world coordinates. */
    public record Origin(int x, int y, int z) {
    }

    /** Extent of the selection in blocks. */
    public record Size(int width, int height, int depth) {
    }

    public static Manifest of(ExportSnapshot snapshot,
                              String modVersion,
                              boolean includeAir,
                              String structureFormat,
                              String structureFile,
                              String shapesFile,
        /** Name of the model file, or null when models were not exported. */
        String modelsFile) {
        return new Manifest(
                McPrintFormat.FORMAT,
                McPrintFormat.FORMAT_VERSION,
                TimeUtil.isoUtc(snapshot.capturedAt()),
                new Generator("VoxelPrint", modVersion, "fabric"),
                snapshot.minecraftVersion(),
                snapshot.dataVersion(),
                snapshot.dimensionId(),
                new Origin(snapshot.originX(), snapshot.originY(), snapshot.originZ()),
                new Size(snapshot.width(), snapshot.height(), snapshot.depth()),
                snapshot.volume(),
                snapshot.nonAirBlockCount(),
                snapshot.palette().size(),
                includeAir,
                // Both are out of scope for now, and stated explicitly so a
                // reader never has to guess whether the data is simply missing.
                false,
                false,
                structureFormat,
                structureFile,
                shapesFile,
                modelsFile);
    }
}
