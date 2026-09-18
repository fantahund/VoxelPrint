package de.tobi.voxelprint.selection;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of one player's two selection corners.
 *
 * <p>Either corner may still be unset. Keeping this immutable means a reader can
 * never observe a half-updated state, which matters because commands and the
 * export worker introduced in a later milestone run on different threads.
 */
public record SelectionState(Optional<SelectionAnchor> first, Optional<SelectionAnchor> second) {

    /** The state of a player who has not selected anything yet. */
    public static final SelectionState EMPTY = new SelectionState(Optional.empty(), Optional.empty());

    public SelectionState {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
    }

    public SelectionState withFirst(SelectionAnchor anchor) {
        return new SelectionState(Optional.of(anchor), second);
    }

    public SelectionState withSecond(SelectionAnchor anchor) {
        return new SelectionState(first, Optional.of(anchor));
    }

    /** True once both corners are set; says nothing about validity. */
    public boolean isComplete() {
        return first.isPresent() && second.isPresent();
    }

    public boolean isEmpty() {
        return first.isEmpty() && second.isEmpty();
    }
}
