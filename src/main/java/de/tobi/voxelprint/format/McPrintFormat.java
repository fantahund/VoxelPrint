package de.tobi.voxelprint.format;

/**
 * Constants of the {@code .mcprint} container.
 *
 * <p>A {@code .mcprint} file is a plain ZIP archive so that any tool, and later
 * the website, can read it without Minecraft.
 */
public final class McPrintFormat {

    /** Identifies the container in {@code manifest.json}. */
    public static final String FORMAT = "mcprint";

    /**
     * Container version.
     *
     * <p>Only bumped when the meaning of an existing field changes. Adding new
     * optional fields keeps the version, and readers are expected to ignore
     * fields they do not know.
     */
    public static final int FORMAT_VERSION = 1;

    /** File extension, including the dot. */
    public static final String EXTENSION = ".mcprint";

    public static final String ENTRY_MANIFEST = "manifest.json";
    public static final String ENTRY_BLOCK_SUMMARY = "block-summary.json";
    public static final String ENTRY_SHAPES = "shapes.json";
    public static final String ENTRY_MODELS = "models.json";

    private McPrintFormat() {
        throw new AssertionError("No instances.");
    }
}
