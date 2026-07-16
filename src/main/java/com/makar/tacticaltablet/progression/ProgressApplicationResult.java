package com.makar.tacticaltablet.progression;

import java.util.Objects;

/** Immutable outcome exposed by the application boundary without leaking mutable progress state. */
record ProgressApplicationResult<T>(T outcome, boolean changed) {
    ProgressApplicationResult {
        Objects.requireNonNull(outcome, "outcome");
    }
}
