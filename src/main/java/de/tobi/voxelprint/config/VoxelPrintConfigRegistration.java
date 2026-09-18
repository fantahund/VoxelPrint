package de.tobi.voxelprint.config;

import de.tobi.voxelprint.schematic.StructureFormat;
import de.voxelmap.voxelconfig.ConfigProvider;
import de.voxelmap.voxelconfig.SettingsCategory;
import de.voxelmap.voxelconfig.SettingsGroup;
import de.voxelmap.voxelconfig.SettingsOption;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;

/**
 * Describes VoxelPrint's settings for VoxelConfig's settings screen.
 *
 * <p>Only options that actually change behaviour are listed, so the screen never
 * shows a control that does nothing.
 *
 * <p>Lives in common code: the classes used here carry no client-only types, so
 * a dedicated server can load this file safely. Only the screen that renders it
 * is client-only.
 */
public final class VoxelPrintConfigRegistration implements ConfigProvider {

    private final VoxelPrintConfig config;

    public VoxelPrintConfigRegistration(VoxelPrintConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public List<SettingsCategory> categories() {
        return List.of(generalCategory(), selectionCategory(), exportCategory(), advancedCategory());
    }

    private SettingsCategory generalCategory() {
        return new SettingsCategory("general", "voxelprint.config.category.general", List.of(
                new SettingsGroup("voxelprint.config.group.files", List.of(
                        SettingsOption.text("exportDirectory",
                                "voxelprint.config.export_directory",
                                "voxelprint.config.export_directory.tooltip",
                                config::exportDirectory,
                                config::setExportDirectory,
                                () -> true, Component::empty)
                )),
                new SettingsGroup("voxelprint.config.group.feedback", List.of(
                        SettingsOption.toggle("showExportNotifications",
                                "voxelprint.config.show_export_notifications",
                                "voxelprint.config.show_export_notifications.tooltip",
                                config::showExportNotifications,
                                config::setShowExportNotifications)
                ))
        ));
    }

    private SettingsCategory exportCategory() {
        return new SettingsCategory("export", "voxelprint.config.category.export", List.of(
                new SettingsGroup("voxelprint.config.group.contents", List.of(
                        SettingsOption.toggle("includeAir",
                                "voxelprint.config.include_air",
                                "voxelprint.config.include_air.tooltip",
                                config::includeAir,
                                config::setIncludeAir),
                        SettingsOption.toggle("createBlockSummary",
                                "voxelprint.config.create_block_summary",
                                "voxelprint.config.create_block_summary.tooltip",
                                config::createBlockSummary,
                                config::setCreateBlockSummary),
                        SettingsOption.toggle("exportBlockShapes",
                                "voxelprint.config.export_block_shapes",
                                "voxelprint.config.export_block_shapes.tooltip",
                                config::exportBlockShapes,
                                config::setExportBlockShapes),
                        SettingsOption.toggle("exportBlockModels",
                                "voxelprint.config.export_block_models",
                                "voxelprint.config.export_block_models.tooltip",
                                config::exportBlockModels,
                                config::setExportBlockModels),
                        SettingsOption.slider("modelColourDetail",
                                "voxelprint.config.model_colour_detail",
                                "voxelprint.config.model_colour_detail.tooltip",
                                () -> (double) config.modelColourDetail(),
                                value -> config.setModelColourDetail(value.intValue()),
                                VoxelPrintConfig.MIN_MODEL_COLOUR_DETAIL,
                                VoxelPrintConfig.MAX_MODEL_COLOUR_DETAIL,
                                1,
                                VoxelPrintConfigRegistration::cells,
                                config::exportBlockModels, Component::empty, 0)
                )),
                new SettingsGroup("voxelprint.config.group.format", List.of(
                        SettingsOption.choice("structureFormat",
                                "voxelprint.config.structure_format",
                                "voxelprint.config.structure_format.tooltip",
                                config::structureFormat,
                                config::setStructureFormat,
                                List.of(
                                        new SettingsOption.Choice<>(StructureFormat.SPONGE_V3,
                                                Component.translatable("voxelprint.config.structure_format.sponge")),
                                        new SettingsOption.Choice<>(StructureFormat.INTERNAL_JSON,
                                                Component.translatable("voxelprint.config.structure_format.json"))))
                )),
                new SettingsGroup("voxelprint.config.group.files", List.of(
                        SettingsOption.toggle("overwriteExistingExports",
                                "voxelprint.config.overwrite_existing_exports",
                                "voxelprint.config.overwrite_existing_exports.tooltip",
                                config::overwriteExistingExports,
                                config::setOverwriteExistingExports)
                ))
        ));
    }

    private SettingsCategory selectionCategory() {
        return new SettingsCategory("selection", "voxelprint.config.category.selection", List.of(
                new SettingsGroup("voxelprint.config.group.limits", List.of(
                        SettingsOption.slider("maxSelectionVolume",
                                "voxelprint.config.max_selection_volume",
                                "voxelprint.config.max_selection_volume.tooltip",
                                () -> (double) config.maxSelectionVolume(),
                                value -> config.setMaxSelectionVolume(value.intValue()),
                                VoxelPrintConfig.MIN_SELECTION_VOLUME,
                                VoxelPrintConfig.MAX_SELECTION_VOLUME,
                                VoxelPrintConfig.MIN_SELECTION_VOLUME,
                                VoxelPrintConfigRegistration::blocks,
                                () -> true, Component::empty, 0),
                        SettingsOption.slider("maxSelectionEdge",
                                "voxelprint.config.max_selection_edge",
                                "voxelprint.config.max_selection_edge.tooltip",
                                () -> (double) config.maxSelectionEdge(),
                                value -> config.setMaxSelectionEdge(value.intValue()),
                                VoxelPrintConfig.MIN_SELECTION_EDGE,
                                VoxelPrintConfig.MAX_SELECTION_EDGE,
                                VoxelPrintConfig.MIN_SELECTION_EDGE,
                                VoxelPrintConfigRegistration::blocks,
                                () -> true, Component::empty, 0)
                )),
                new SettingsGroup("voxelprint.config.group.display", List.of(
                        SettingsOption.toggle("showSelectionOutline",
                                "voxelprint.config.show_selection_outline",
                                "voxelprint.config.show_selection_outline.tooltip",
                                config::showSelectionOutline,
                                config::setShowSelectionOutline)
                ))
        ));
    }

    private SettingsCategory advancedCategory() {
        return new SettingsCategory("advanced", "voxelprint.config.category.advanced", List.of(
                new SettingsGroup("voxelprint.config.group.performance", List.of(
                        SettingsOption.toggle("asynchronousFileWriting",
                                "voxelprint.config.asynchronous_file_writing",
                                "voxelprint.config.asynchronous_file_writing.tooltip",
                                config::asynchronousFileWriting,
                                config::setAsynchronousFileWriting)
                )),
                new SettingsGroup("voxelprint.config.group.diagnostics", List.of(
                        SettingsOption.toggle("debugLogging",
                                "voxelprint.config.debug_logging",
                                "voxelprint.config.debug_logging.tooltip",
                                config::debugLogging,
                                config::setDebugLogging)
                ))
        ));
    }

    /** Slider label, for example {@code 1048576 blocks}. */
    private static Component blocks(Double value) {
        return Component.translatable("voxelprint.config.blocks", value.longValue());
    }

    /** Reads a detail level as what it means: how many pieces a face may become. */
    private static Component cells(Double value) {
        long side = value.longValue();
        return side <= 1
                ? Component.translatable("voxelprint.config.model_colour_detail.flat")
                : Component.translatable("voxelprint.config.cells", side, side * side);
    }
}
