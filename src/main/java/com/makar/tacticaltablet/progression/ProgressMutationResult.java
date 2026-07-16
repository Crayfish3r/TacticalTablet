package com.makar.tacticaltablet.progression;

/** Immutable outcome of a single numeric progress mutation. */
public record ProgressMutationResult(
        boolean changed,
        int previousValue,
        int currentValue
) {
}
