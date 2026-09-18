package de.tobi.voxelprint.util;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Timestamps for export metadata.
 */
public final class TimeUtil {

    private TimeUtil() {
        throw new AssertionError("No instances.");
    }

    /**
     * Formats an instant as ISO-8601 in UTC, for example {@code 2026-09-17T09:00:00Z}.
     *
     * <p>Always UTC, never the local zone: a local offset would leak roughly
     * where the author lives into every exported file.
     */
    public static String isoUtc(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    }
}
