package de.tobi.voxelprint.selection;

import de.tobi.voxelprint.config.VoxelPrintConfig;
import java.util.Objects;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;

public final class SelectionOutline {

    private final SelectionManager selections;
    private final VoxelPrintConfig config;

    public SelectionOutline(SelectionManager selections, VoxelPrintConfig config) {
        this.selections = Objects.requireNonNull(selections, "selections");
        this.config = Objects.requireNonNull(config, "config");

        LevelRenderEvents.BEFORE_GIZMOS.register(this::onBeforeGizmos);
    }

    public void onEndClientTick(Minecraft client) {
    }

    private void onBeforeGizmos(LevelRenderContext context) {
        if (!config.showSelectionOutline()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }

        SelectionState state = selections.get();
        if (!state.isComplete()) {
            return;
        }

        SelectionAnchor first = state.first().orElseThrow();
        SelectionAnchor second = state.second().orElseThrow();
        if (!first.dimension().equals(second.dimension())
                || !client.level.dimension().equals(first.dimension())) {
            return;
        }

        Selection selection = Selection.between(first.dimension(), first.position(), second.position());

        net.minecraft.world.phys.AABB aabb = new net.minecraft.world.phys.AABB(
                selection.min().getX() - 0.005,
                selection.min().getY() - 0.005,
                selection.min().getZ() - 0.005,
                selection.max().getX() + 1.005,
                selection.max().getY() + 1.005,
                selection.max().getZ() + 1.005
        );

        int color = 0xFF55FF55; // True Emerald Green
        net.minecraft.gizmos.Gizmos.cuboid(aabb, net.minecraft.gizmos.GizmoStyle.stroke(color));
    }
}
