package de.tobi.voxelprint.export;

import de.tobi.voxelprint.VoxelPrint;
import de.tobi.voxelprint.config.VoxelPrintConfig;
import de.tobi.voxelprint.format.BlockModels;
import de.tobi.voxelprint.format.BlockShapes;
import de.tobi.voxelprint.schematic.SchematicWriter;
import de.tobi.voxelprint.selection.Selection;
import de.tobi.voxelprint.util.FileNameValidator;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Runs exports.
 *
 * <p>The split between threads is the point of this class:
 *
 * <ol>
 *   <li>name check, in-flight check and chunk check happen on the client thread;
 *   <li>the world is read on the client thread into an immutable snapshot;
 *   <li>JSON, ZIP and the actual file write happen on a worker thread that can
 *       no longer reach any world data;
 *   <li>the answer is handed back onto the client thread before it is shown.
 * </ol>
 *
 * <p>No lock is needed anywhere, because nothing is both shared and mutable.
 */
public final class ExportService {

    private final VoxelPrintConfig config;
    private final ExportFileManager files;

    /** One export at a time, enforced by compare and set. */
    private final AtomicBoolean running = new AtomicBoolean();

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "voxelprint-export");
        // Daemon: a pending export must never keep the game from shutting down.
        thread.setDaemon(true);
        return thread;
    });

    public ExportService(VoxelPrintConfig config, ExportFileManager files) {
        this.config = Objects.requireNonNull(config, "config");
        this.files = Objects.requireNonNull(files, "files");
    }

    /**
     * Starts an export.
     *
     * @param onFinished called on the client thread once the file has been
     *                   written or has failed; not called when this method
     *                   already reports a problem
     * @return the immediate outcome
     */
    public ExportResult start(Minecraft client,
                              ClientLevel level,
                              Selection selection,
                              String rawName,
                              Consumer<ExportResult> onFinished) {
        Optional<String> name = FileNameValidator.validate(rawName);
        if (name.isEmpty()) {
            return ExportResult.of(ExportResult.Status.INVALID_NAME);
        }

        if (!running.compareAndSet(false, true)) {
            return ExportResult.of(ExportResult.Status.ALREADY_RUNNING);
        }

        try {
            if (!level.dimension().equals(selection.dimension())
                    || !SnapshotCapture.isFullyLoaded(level, selection)) {
                running.set(false);
                return ExportResult.of(ExportResult.Status.CHUNKS_NOT_LOADED);
            }

            long startedAt = System.nanoTime();
            ExportSnapshot snapshot = SnapshotCapture.capture(level, selection, config.exportBlockShapes(),
                    config.exportBlockModels() ? client : null, config.modelColourDetail());
            config.debug("Snapshot of {} blocks, {} states, {} shaped, {} quads, {} materials, in {} ms",
                    snapshot.volume(), snapshot.palette().size(),
                    BlockShapes.detailedCount(snapshot),
                    BlockModels.quadCount(snapshot), snapshot.materials().size(),
                    (System.nanoTime() - startedAt) / 1_000_000L);

            Runnable job = () -> {
                ExportResult result = writeArchive(snapshot, name.get());
                client.execute(() -> {
                    running.set(false);
                    onFinished.accept(result);
                });
            };

            if (config.asynchronousFileWriting()) {
                worker.execute(job);
            } else {
                job.run();
            }
            return ExportResult.of(ExportResult.Status.ACCEPTED);
        } catch (RuntimeException e) {
            running.set(false);
            VoxelPrint.LOGGER.error("Failed to prepare export", e);
            return ExportResult.of(ExportResult.Status.WRITE_FAILED);
        }
    }

    /** Runs on the worker thread and touches nothing but the snapshot. */
    private ExportResult writeArchive(ExportSnapshot snapshot, String name) {
        try {
            Path target = files.resolveTarget(name);
            // The only place the structure format matters: everything else in
            // the pipeline works the same whichever writer is chosen.
            SchematicWriter structureWriter = config.structureFormat().createWriter();
            McPrintArchiveWriter writer = new McPrintArchiveWriter(
                    structureWriter,
                    VoxelPrint.version(),
                    config.includeAir(),
                    config.createBlockSummary(),
                    config.exportBlockShapes(),
                    config.exportBlockModels());

            files.write(target, out -> writer.write(snapshot, out));

            String location = files.describe(target);
            VoxelPrint.LOGGER.info("Exported {} blocks to {}", snapshot.volume(), location);
            return ExportResult.success(target.getFileName().toString(), location);
        } catch (FileAlreadyExistsException e) {
            return ExportResult.of(ExportResult.Status.FILE_EXISTS);
        } catch (IOException | RuntimeException e) {
            // The player gets a sentence, the log gets the cause.
            VoxelPrint.LOGGER.error("Export failed", e);
            return ExportResult.of(ExportResult.Status.WRITE_FAILED);
        }
    }
}
