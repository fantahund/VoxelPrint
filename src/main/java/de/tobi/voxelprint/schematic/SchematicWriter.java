package de.tobi.voxelprint.schematic;

import de.tobi.voxelprint.export.ExportSnapshot;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes the block structure of a snapshot into the {@code .mcprint} container.
 *
 * <p>The interface exists so that the Sponge Schematic writer can be added as a
 * second implementation without touching the export pipeline: only the chosen
 * implementation changes, while the archive writer, the manifest and the block
 * summary stay exactly as they are.
 */
public interface SchematicWriter {

    /** Value written to {@code structureFormat} in the manifest. */
    String formatId();

    /** Name of the entry inside the archive. */
    String fileName();

    /**
     * Writes the structure.
     *
     * <p>Implementations need not worry about closing: the archive hands over a
     * view whose {@code close} only flushes, so a library that insists on
     * closing its output cannot tear down the surrounding archive.
     */
    void write(ExportSnapshot snapshot, OutputStream out) throws IOException;
}
