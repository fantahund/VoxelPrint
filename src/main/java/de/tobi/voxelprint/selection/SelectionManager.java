package de.tobi.voxelprint.selection;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Holds the selection.
 *
 * <p>Exactly one, because VoxelPrint is a client side mod: there is only ever
 * the player sitting at this computer. An earlier version kept a map per player
 * UUID, which made sense while the commands ran on the server and stopped making
 * sense the moment they moved to the client.
 *
 * <p>Still concurrency safe. Commands run on the client thread, the export
 * worker runs on its own, and the state is read from both. An
 * {@link AtomicReference} over an immutable value keeps that correct without a
 * lock, and a reader can never observe a half-updated selection.
 */
public final class SelectionManager {

    private final AtomicReference<SelectionState> state = new AtomicReference<>(SelectionState.EMPTY);

    /** Returns the current state, never {@code null}. */
    public SelectionState get() {
        return state.get();
    }

    public SelectionState setFirst(SelectionAnchor anchor) {
        return update(current -> current.withFirst(anchor));
    }

    public SelectionState setSecond(SelectionAnchor anchor) {
        return update(current -> current.withSecond(anchor));
    }

    /**
     * Drops the selection.
     *
     * <p>Also called when leaving a world, so a selection made in one world does
     * not silently survive into the next.
     *
     * @return whether there was anything to drop
     */
    public boolean clear() {
        return state.getAndSet(SelectionState.EMPTY) != SelectionState.EMPTY;
    }

    private SelectionState update(UnaryOperator<SelectionState> change) {
        return state.updateAndGet(change);
    }
}
