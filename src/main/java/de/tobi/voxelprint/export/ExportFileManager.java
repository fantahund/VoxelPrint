package de.tobi.voxelprint.export;

import de.tobi.voxelprint.config.VoxelPrintConfig;
import de.tobi.voxelprint.format.McPrintFormat;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Decides where an export goes and puts it there safely.
 *
 * <p>Two separate defences against writing outside the export directory. The
 * file name is already restricted to an allow list, and on top of that the
 * resolved path is normalised and checked to still be inside its directory --
 * because the export directory itself comes from the configuration file, which a
 * player on a server may be able to influence and which could otherwise contain
 * {@code ../../}.
 */
public final class ExportFileManager {

    private final Path gameDirectory;
    private final VoxelPrintConfig config;

    public ExportFileManager(Path gameDirectory, VoxelPrintConfig config) {
        this.gameDirectory = Objects.requireNonNull(gameDirectory, "gameDirectory").toAbsolutePath().normalize();
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Resolves the configured export directory.
     *
     * @throws IOException when the configured value would leave the game directory
     */
    public Path exportDirectory() throws IOException {
        Path resolved = gameDirectory.resolve(config.exportDirectory()).normalize();
        if (!resolved.startsWith(gameDirectory)) {
            throw new IOException("Export directory escapes the game directory: " + config.exportDirectory());
        }
        return resolved;
    }

    /** Full path of the archive for an already validated name. */
    public Path resolveTarget(String validatedName) throws IOException {
        Path directory = exportDirectory();
        Path target = directory.resolve(validatedName + McPrintFormat.EXTENSION).normalize();
        if (!target.startsWith(directory)) {
            throw new IOException("Export file escapes the export directory: " + validatedName);
        }
        return target;
    }

    /** Path as shown to the player, relative to the game directory. */
    public String describe(Path target) {
        return gameDirectory.relativize(target).toString().replace('\\', '/');
    }

    /**
     * Writes through a temporary file and only then moves it into place.
     *
     * <p>A crash halfway through therefore leaves a stray temporary file rather
     * than a truncated {@code .mcprint} that later blocks its own name.
     */
    public void write(Path target, Content content) throws IOException {
        if (Files.exists(target) && !config.overwriteExistingExports()) {
            throw new FileAlreadyExistsException(target.toString());
        }

        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".voxelprint-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temporary)) {
                content.writeTo(out);
            }
            moveIntoPlace(temporary, target);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temporary);
            throw e;
        }
    }

    private static void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Some file systems cannot move atomically. Still better than
            // writing into the target directly.
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** What to write into the archive. */
    @FunctionalInterface
    public interface Content {
        void writeTo(OutputStream out) throws IOException;
    }
}
