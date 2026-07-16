package com.makar.tacticaltablet.progression;

import java.util.Objects;

/** Pure purchase decision made before the owning service mutates its state. */
public record ProgressPurchaseResult(
        boolean successful,
        Failure failure,
        int previousBalance,
        int currentBalance
) {
    public ProgressPurchaseResult {
        Objects.requireNonNull(failure, "failure");
        if (successful != (failure == Failure.NONE)) {
            throw new IllegalArgumentException("Successful purchases must have no failure");
        }
        if (!successful && previousBalance != currentBalance) {
            throw new IllegalArgumentException("Rejected purchases must preserve the balance");
        }
    }

    public enum Failure {
        NONE,
        ALREADY_OWNED,
        INSUFFICIENT_FUNDS,
        INVALID_ITEM
    }
}
