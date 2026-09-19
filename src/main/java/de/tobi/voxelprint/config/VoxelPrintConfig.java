package de.tobi.voxelprint.config;

import de.tobi.voxelprint.VoxelPrint;
import de.tobi.voxelprint.schematic.StructureFormat;
import de.voxelmap.voxelconfig.ConfigFile;
import java.nio.file.Path;
import java.util.Objects;

/**
 * VoxelPrint's settings, persisted through VoxelConfig.
 *
 * <p>Storage is VoxelConfig's own {@code Key:Value} file, so there is no second,
 * parallel configuration format. The class holds nothing but values: it never
 * touches the world, the network or the file system beyond that one file.
 *
 * <p>Fields are {@code volatile} because they are written from the client thread
 * when the settings screen is used and read from the server thread while a
 * command runs. Each value is an independent scalar, so no wider locking is
 * needed.
 */
public final class VoxelPrintConfig {

    public static final int DEFAULT_MAX_SELECTION_VOLUME = 1_048_576;
    public static final int DEFAULT_MAX_SELECTION_EDGE = 256;
    public static final boolean DEFAULT_DEBUG_LOGGING = false;
    public static final boolean DEFAULT_SHOW_SELECTION_OUTLINE = true;
    public static final String DEFAULT_EXPORT_DIRECTORY = "voxelprint/exports";
    public static final boolean DEFAULT_SHOW_EXPORT_NOTIFICATIONS = true;
    public static final boolean DEFAULT_INCLUDE_AIR = false;
    public static final boolean DEFAULT_OVERWRITE_EXISTING_EXPORTS = false;
    public static final boolean DEFAULT_CREATE_BLOCK_SUMMARY = true;
    public static final boolean DEFAULT_EXPORT_BLOCK_SHAPES = true;
    public static final boolean DEFAULT_EXPORT_BLOCK_MODELS = true;
    public static final int DEFAULT_MODEL_COLOUR_DETAIL = 4;
    public static final boolean DEFAULT_ASYNCHRONOUS_FILE_WRITING = true;
    public static final StructureFormat DEFAULT_STRUCTURE_FORMAT = StructureFormat.SPONGE_V3;
    /**
     * Where an export is sent when somebody clicks Upload.
     *
     * <p>Configurable because the platform is a small server anyone can run for
     * themselves; blank switches uploading off and leaves only the folder.
     */
    public static final String DEFAULT_WEB_PLATFORM_URL = "https://voxelprint.emrion.de";

    public static final int MIN_SELECTION_VOLUME = 4_096;
    public static final int MAX_SELECTION_VOLUME = 8_388_608;
    public static final int MIN_SELECTION_EDGE = 16;
    public static final int MAX_SELECTION_EDGE = 4_096;

    /**
     * How finely a face may be cut up to follow its texture.
     *
     * <p>One means a face keeps a single colour, which is what it had before
     * this existed. Higher follows the texture more closely at the cost of
     * faces in the file; eight is already finer than a printer can lay down.
     */
    public static final int MIN_MODEL_COLOUR_DETAIL = 1;
    public static final int MAX_MODEL_COLOUR_DETAIL = 8;

    private static final String KEY_MAX_VOLUME = "Max Selection Volume";
    private static final String KEY_MAX_EDGE = "Max Selection Edge";
    private static final String KEY_DEBUG_LOGGING = "Debug Logging";
    private static final String KEY_SHOW_OUTLINE = "Show Selection Outline";
    private static final String KEY_EXPORT_DIRECTORY = "Export Directory";
    private static final String KEY_SHOW_NOTIFICATIONS = "Show Export Notifications";
    private static final String KEY_INCLUDE_AIR = "Include Air";
    private static final String KEY_OVERWRITE = "Overwrite Existing Exports";
    private static final String KEY_BLOCK_SUMMARY = "Create Block Summary";
    private static final String KEY_BLOCK_SHAPES = "Export Block Shapes";
    private static final String KEY_BLOCK_MODELS = "Export Block Models";
    private static final String KEY_MODEL_COLOUR_DETAIL = "Model Colour Detail";
    private static final String KEY_ASYNC_WRITING = "Asynchronous File Writing";
    private static final String KEY_STRUCTURE_FORMAT = "Structure Format";
    private static final String KEY_WEB_PLATFORM_URL = "Web Platform URL";

    private final ConfigFile file;

    private volatile int maxSelectionVolume = DEFAULT_MAX_SELECTION_VOLUME;
    private volatile int maxSelectionEdge = DEFAULT_MAX_SELECTION_EDGE;
    private volatile boolean debugLogging = DEFAULT_DEBUG_LOGGING;
    private volatile boolean showSelectionOutline = DEFAULT_SHOW_SELECTION_OUTLINE;
    private volatile String exportDirectory = DEFAULT_EXPORT_DIRECTORY;
    private volatile boolean showExportNotifications = DEFAULT_SHOW_EXPORT_NOTIFICATIONS;
    private volatile boolean includeAir = DEFAULT_INCLUDE_AIR;
    private volatile boolean overwriteExistingExports = DEFAULT_OVERWRITE_EXISTING_EXPORTS;
    private volatile boolean createBlockSummary = DEFAULT_CREATE_BLOCK_SUMMARY;
    private volatile boolean exportBlockShapes = DEFAULT_EXPORT_BLOCK_SHAPES;
    private volatile boolean exportBlockModels = DEFAULT_EXPORT_BLOCK_MODELS;
    private volatile int modelColourDetail = DEFAULT_MODEL_COLOUR_DETAIL;
    private volatile boolean asynchronousFileWriting = DEFAULT_ASYNCHRONOUS_FILE_WRITING;
    private volatile StructureFormat structureFormat = DEFAULT_STRUCTURE_FORMAT;
    private volatile String webPlatformUrl = DEFAULT_WEB_PLATFORM_URL;

    public VoxelPrintConfig(Path path) {
        this.file = new ConfigFile(Objects.requireNonNull(path, "path"));
    }

    /** Reads the file, falling back to defaults for anything missing or broken. */
    public void load() {
        file.load(reader -> {
            maxSelectionVolume = reader.getInt(KEY_MAX_VOLUME,
                    DEFAULT_MAX_SELECTION_VOLUME, MIN_SELECTION_VOLUME, MAX_SELECTION_VOLUME);
            maxSelectionEdge = reader.getInt(KEY_MAX_EDGE,
                    DEFAULT_MAX_SELECTION_EDGE, MIN_SELECTION_EDGE, MAX_SELECTION_EDGE);
            debugLogging = reader.getBoolean(KEY_DEBUG_LOGGING, DEFAULT_DEBUG_LOGGING);
            showSelectionOutline = reader.getBoolean(KEY_SHOW_OUTLINE, DEFAULT_SHOW_SELECTION_OUTLINE);
            setExportDirectory(reader.getString(KEY_EXPORT_DIRECTORY, DEFAULT_EXPORT_DIRECTORY));
            showExportNotifications = reader.getBoolean(KEY_SHOW_NOTIFICATIONS, DEFAULT_SHOW_EXPORT_NOTIFICATIONS);
            includeAir = reader.getBoolean(KEY_INCLUDE_AIR, DEFAULT_INCLUDE_AIR);
            overwriteExistingExports = reader.getBoolean(KEY_OVERWRITE, DEFAULT_OVERWRITE_EXISTING_EXPORTS);
            createBlockSummary = reader.getBoolean(KEY_BLOCK_SUMMARY, DEFAULT_CREATE_BLOCK_SUMMARY);
            exportBlockShapes = reader.getBoolean(KEY_BLOCK_SHAPES, DEFAULT_EXPORT_BLOCK_SHAPES);
            exportBlockModels = reader.getBoolean(KEY_BLOCK_MODELS, DEFAULT_EXPORT_BLOCK_MODELS);
            modelColourDetail = reader.getInt(KEY_MODEL_COLOUR_DETAIL,
                    DEFAULT_MODEL_COLOUR_DETAIL, MIN_MODEL_COLOUR_DETAIL, MAX_MODEL_COLOUR_DETAIL);
            asynchronousFileWriting = reader.getBoolean(KEY_ASYNC_WRITING, DEFAULT_ASYNCHRONOUS_FILE_WRITING);
            structureFormat = StructureFormat.fromId(
                    reader.getString(KEY_STRUCTURE_FORMAT, DEFAULT_STRUCTURE_FORMAT.id()),
                    DEFAULT_STRUCTURE_FORMAT);
            setWebPlatformUrl(reader.getString(KEY_WEB_PLATFORM_URL, DEFAULT_WEB_PLATFORM_URL));
        });
        VoxelPrint.LOGGER.info("Configuration loaded: maxSelectionVolume={}, maxSelectionEdge={}, debugLogging={}",
                maxSelectionVolume, maxSelectionEdge, debugLogging);
    }

    /**
     * Writes the file.
     *
     * <p>Called by the settings screen when it closes, so a change the player
     * made survives without a separate "apply" step.
     */
    public void save() {
        file.save(writer -> {
            writer.comment("VoxelPrint configuration");
            writer.comment("Edit in game via Mod Menu or /voxelprint config.");
            writer.blank();
            writer.put(KEY_MAX_VOLUME, maxSelectionVolume);
            writer.put(KEY_MAX_EDGE, maxSelectionEdge);
            writer.put(KEY_DEBUG_LOGGING, debugLogging);
            writer.put(KEY_SHOW_OUTLINE, showSelectionOutline);
            writer.put(KEY_EXPORT_DIRECTORY, exportDirectory);
            writer.put(KEY_SHOW_NOTIFICATIONS, showExportNotifications);
            writer.put(KEY_INCLUDE_AIR, includeAir);
            writer.put(KEY_OVERWRITE, overwriteExistingExports);
            writer.put(KEY_BLOCK_SUMMARY, createBlockSummary);
            writer.put(KEY_BLOCK_SHAPES, exportBlockShapes);
            writer.put(KEY_BLOCK_MODELS, exportBlockModels);
            writer.put(KEY_MODEL_COLOUR_DETAIL, modelColourDetail);
            writer.put(KEY_ASYNC_WRITING, asynchronousFileWriting);
            writer.put(KEY_STRUCTURE_FORMAT, structureFormat.id());
            writer.put(KEY_WEB_PLATFORM_URL, webPlatformUrl);
        });
        debug("Configuration saved: maxSelectionVolume={}, maxSelectionEdge={}",
                maxSelectionVolume, maxSelectionEdge);
    }

    public int maxSelectionVolume() {
        return maxSelectionVolume;
    }

    public void setMaxSelectionVolume(int value) {
        maxSelectionVolume = Math.clamp(value, MIN_SELECTION_VOLUME, MAX_SELECTION_VOLUME);
    }

    public int maxSelectionEdge() {
        return maxSelectionEdge;
    }

    public void setMaxSelectionEdge(int value) {
        maxSelectionEdge = Math.clamp(value, MIN_SELECTION_EDGE, MAX_SELECTION_EDGE);
    }

    public boolean showSelectionOutline() {
        return showSelectionOutline;
    }

    public void setShowSelectionOutline(boolean value) {
        showSelectionOutline = value;
    }

    public boolean debugLogging() {
        return debugLogging;
    }

    public void setDebugLogging(boolean value) {
        debugLogging = value;
    }

    public String exportDirectory() {
        return exportDirectory;
    }

    /** Where Upload sends an export, without a trailing slash, or empty for off. */
    public String webPlatformUrl() {
        return webPlatformUrl;
    }

    /**
     * Sets the website an export can be uploaded to.
     *
     * <p>Only http and https are accepted, and anything else is treated as
     * blank: this value ends up in a request the mod makes on the player's
     * behalf, and a config file is not the place to be talked into some other
     * protocol. A trailing slash is dropped so paths can be appended plainly.
     */
    public void setWebPlatformUrl(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        webPlatformUrl = lower.startsWith("http://") || lower.startsWith("https://") ? trimmed : "";
    }

    /**
     * Sets the export directory, relative to the game directory.
     *
     * <p>Blank input falls back to the default. Whether the value actually stays
     * inside the game directory is checked again where the path is resolved --
     * this setter cannot know the game directory, and a single check in one
     * place is worth less than a check right before the file is written.
     */
    public void setExportDirectory(String value) {
        String trimmed = value == null ? "" : value.trim();
        exportDirectory = trimmed.isEmpty() ? DEFAULT_EXPORT_DIRECTORY : trimmed;
    }

    public boolean showExportNotifications() {
        return showExportNotifications;
    }

    public void setShowExportNotifications(boolean value) {
        showExportNotifications = value;
    }

    public boolean includeAir() {
        return includeAir;
    }

    public void setIncludeAir(boolean value) {
        includeAir = value;
    }

    public boolean overwriteExistingExports() {
        return overwriteExistingExports;
    }

    public void setOverwriteExistingExports(boolean value) {
        overwriteExistingExports = value;
    }

    public boolean createBlockSummary() {
        return createBlockSummary;
    }

    public void setCreateBlockSummary(boolean value) {
        createBlockSummary = value;
    }

    public boolean exportBlockShapes() {
        return exportBlockShapes;
    }

    public void setExportBlockShapes(boolean value) {
        exportBlockShapes = value;
    }

    public boolean exportBlockModels() {
        return exportBlockModels;
    }

    public void setExportBlockModels(boolean value) {
        exportBlockModels = value;
    }

    public int modelColourDetail() {
        return modelColourDetail;
    }

    public void setModelColourDetail(int value) {
        modelColourDetail = Math.clamp(value, MIN_MODEL_COLOUR_DETAIL, MAX_MODEL_COLOUR_DETAIL);
    }

    public boolean asynchronousFileWriting() {
        return asynchronousFileWriting;
    }

    public void setAsynchronousFileWriting(boolean value) {
        asynchronousFileWriting = value;
    }

    public StructureFormat structureFormat() {
        return structureFormat;
    }

    public void setStructureFormat(StructureFormat value) {
        structureFormat = value == null ? DEFAULT_STRUCTURE_FORMAT : value;
    }

    /**
     * Logs a diagnostic line, but only while debug logging is switched on.
     *
     * <p>Deliberately logged at INFO rather than DEBUG: Minecraft's default log
     * configuration hides DEBUG, which would make an opt-in setting look broken.
     */
    public void debug(String message, Object... args) {
        if (debugLogging) {
            VoxelPrint.LOGGER.info("[debug] " + message, args);
        }
    }
}
