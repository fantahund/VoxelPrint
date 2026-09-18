package de.tobi.voxelprint.format;

/**
 * One line of {@code block-summary.json}.
 *
 * @param id    plain block id, for example {@code minecraft:oak_stairs}
 * @param state full canonical block state with all properties
 * @param count how often the state occurs in the selection
 */
public record BlockSummaryEntry(String id, String state, long count) {
}
