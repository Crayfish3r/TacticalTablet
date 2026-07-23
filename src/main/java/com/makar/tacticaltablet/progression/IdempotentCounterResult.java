package com.makar.tacticaltablet.progression;

import java.util.Objects;
import java.util.Optional;

record IdempotentCounterResult(
        IdempotentCreditStatus status,
        int previousValue,
        int currentValue,
        String diagnostic,
        Optional<IdempotentCounterRollback> rollback
) {
    IdempotentCounterResult {
        Objects.requireNonNull(status, "status");
        diagnostic = diagnostic == null ? "" : diagnostic;
        rollback = rollback == null ? Optional.empty() : rollback;
    }
}

record IdempotentCounterRollback(
        MutableProgressState.Counter counter,
        int previousValue,
        int appliedValue,
        ProgressReceipt receipt
) {
    IdempotentCounterRollback {
        Objects.requireNonNull(counter, "counter");
        Objects.requireNonNull(receipt, "receipt");
    }
}
