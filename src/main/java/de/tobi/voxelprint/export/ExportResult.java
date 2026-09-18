package de.tobi.voxelprint.export;

/**
 * Outcome of an export, both the immediate answer and the one that arrives once
 * the file has been written.
 *
 * @param status    what happened
 * @param fileName  file name on success, otherwise empty
 * @param location  path relative to the game directory on success, otherwise empty
 */
public record ExportResult(Status status, String fileName, String location) {

    public enum Status {
        /** Snapshot taken, the file is being written. */
        ACCEPTED,
        /** The name contained characters that are not allowed. */
        INVALID_NAME,
        /** This player already has an export in flight. */
        ALREADY_RUNNING,
        /** Part of the selection is not loaded, so it cannot be read. */
        CHUNKS_NOT_LOADED,
        /** The file exists and overwriting is switched off. */
        FILE_EXISTS,
        /** Writing failed; the reason is in the log. */
        WRITE_FAILED,
        /** The file is on disk. */
        SUCCESS
    }

    public static ExportResult of(Status status) {
        return new ExportResult(status, "", "");
    }

    public static ExportResult success(String fileName, String location) {
        return new ExportResult(Status.SUCCESS, fileName, location);
    }
}
