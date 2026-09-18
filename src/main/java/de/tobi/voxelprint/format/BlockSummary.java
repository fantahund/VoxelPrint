package de.tobi.voxelprint.format;

import de.tobi.voxelprint.export.ExportSnapshot;
import de.tobi.voxelprint.export.PaletteEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Contents of {@code block-summary.json}.
 *
 * <p>Ordering is fully deterministic: by count descending, then by state
 * ascending. Two exports of the same build therefore produce byte-identical
 * summaries, which makes them comparable and diffable.
 */
public record BlockSummary(
        boolean includeAir,
        int volume,
        long nonAirBlockCount,
        int uniqueBlockTypes,
        int uniqueBlockStates,
        List<BlockSummaryEntry> blocks) {

    /**
     * Builds the summary from a snapshot.
     *
     * @param includeAir when false, air is left out of the listing entirely
     */
    public static BlockSummary of(ExportSnapshot snapshot, boolean includeAir) {
        List<PaletteEntry> palette = snapshot.palette();
        List<BlockSummaryEntry> entries = new ArrayList<>(palette.size());

        for (int index = 0; index < palette.size(); index++) {
            PaletteEntry entry = palette.get(index);
            if (entry.air() && !includeAir) {
                continue;
            }
            entries.add(new BlockSummaryEntry(entry.blockId(), entry.blockState(), snapshot.count(index)));
        }

        entries.sort(Comparator.comparingLong(BlockSummaryEntry::count).reversed()
                .thenComparing(BlockSummaryEntry::state));

        long distinctTypes = entries.stream().map(BlockSummaryEntry::id).distinct().count();

        return new BlockSummary(
                includeAir,
                snapshot.volume(),
                snapshot.nonAirBlockCount(),
                (int) distinctTypes,
                entries.size(),
                List.copyOf(entries));
    }
}
