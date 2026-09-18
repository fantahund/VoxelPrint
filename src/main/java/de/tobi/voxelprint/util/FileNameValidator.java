package de.tobi.voxelprint.util;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns a name a player typed into a name that is safe to put on disk.
 *
 * <p>Works as an allow list, not a block list. A block list has to anticipate
 * every attack -- {@code ..}, {@code ..%2f}, backslashes, NUL bytes, unicode
 * look-alikes, trailing dots and spaces that Windows silently strips. An allow
 * list only has to name what is acceptable, and everything else is rejected by
 * construction.
 */
public final class FileNameValidator {

    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    /**
     * Names Windows refuses to use, with or without an extension.
     *
     * <p>Creating {@code con.mcprint} fails in ways that look like a bug in the
     * mod, so these are rejected up front with a clear message instead.
     */
    private static final Set<String> RESERVED = Set.of(
            "con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    private FileNameValidator() {
        throw new AssertionError("No instances.");
    }

    /**
     * Returns the accepted name, or empty when it must be rejected.
     *
     * <p>The returned value contains only characters from the allow list, so it
     * cannot contain a path separator and cannot escape a directory.
     */
    public static Optional<String> validate(String raw) {
        if (raw == null || !ALLOWED.matcher(raw).matches()) {
            return Optional.empty();
        }
        if (RESERVED.contains(raw.toLowerCase(Locale.ROOT))) {
            return Optional.empty();
        }
        return Optional.of(raw);
    }
}
