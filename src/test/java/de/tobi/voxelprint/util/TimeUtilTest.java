package de.tobi.voxelprint.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimeUtilTest {

    @Test
    @DisplayName("formats in UTC, never in the local zone")
    void formatsInUtc() {
        assertEquals("2026-09-17T10:00:00Z", TimeUtil.isoUtc(Instant.parse("2026-09-17T10:00:00Z")));
    }

    @Test
    @DisplayName("drops sub second precision so timestamps stay comparable")
    void truncatesToSeconds() {
        assertEquals("2026-09-17T10:00:00Z", TimeUtil.isoUtc(Instant.parse("2026-09-17T10:00:00.123456Z")));
    }

    @Test
    @DisplayName("the result never carries a local offset")
    void neverCarriesAnOffset() {
        String formatted = TimeUtil.isoUtc(Instant.now());
        assertTrue(formatted.endsWith("Z"), formatted);
    }
}
