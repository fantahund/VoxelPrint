package de.tobi.voxelprint.selection;

import java.util.Optional;

/**
 * Checks whether a player's selection may be used.
 *
 * <p>Reports problems as data instead of throwing: a rejected selection is an
 * everyday situation, not an exceptional one, and the command layer still wants
 * the measurements so it can show them alongside the reason.
 */
public final class SelectionValidator {

    /** Why a selection was rejected. */
    public enum Problem {
        NO_POSITIONS,
        DIMENSION_MISMATCH,
        EDGE_EXCEEDED,
        VOLUME_EXCEEDED
    }

    /**
     * Upper bounds a selection has to stay within.
     *
     * <p>Passed in rather than read from a global, so the caller decides where
     * the numbers come from. Defaults live in the configuration class.
     */
    public record Limits(long maxVolume, int maxEdge) {

        public Limits {
            if (maxVolume < 1L) {
                throw new IllegalArgumentException("maxVolume must be positive: " + maxVolume);
            }
            if (maxEdge < 1) {
                throw new IllegalArgumentException("maxEdge must be positive: " + maxEdge);
            }
        }
    }

    /**
     * Outcome of a validation.
     *
     * @param problem   empty when the selection is usable
     * @param selection present as soon as both corners share a dimension, even
     *                  if the cuboid was then rejected for being too large
     */
    public record Result(Optional<Problem> problem, Optional<Selection> selection, Limits limits) {

        public boolean isValid() {
            return problem.isEmpty();
        }
    }

    private SelectionValidator() {
        throw new AssertionError("No instances.");
    }

    public static Result validate(SelectionState state, Limits limits) {
        if (!state.isComplete()) {
            return new Result(Optional.of(Problem.NO_POSITIONS), Optional.empty(), limits);
        }

        SelectionAnchor first = state.first().orElseThrow();
        SelectionAnchor second = state.second().orElseThrow();
        if (!first.dimension().equals(second.dimension())) {
            return new Result(Optional.of(Problem.DIMENSION_MISMATCH), Optional.empty(), limits);
        }

        Selection selection = Selection.between(first.dimension(), first.position(), second.position());

        // Edge before volume: a long, thin selection is more usefully explained
        // by the edge limit than by a volume it may not even reach.
        if (selection.longestEdge() > limits.maxEdge()) {
            return new Result(Optional.of(Problem.EDGE_EXCEEDED), Optional.of(selection), limits);
        }
        if (selection.volume() > limits.maxVolume()) {
            return new Result(Optional.of(Problem.VOLUME_EXCEEDED), Optional.of(selection), limits);
        }
        return new Result(Optional.empty(), Optional.of(selection), limits);
    }
}
