package com.makar.tacticaltablet.progression;

import java.util.Objects;
import java.util.Optional;

record IdempotentCreditResult(
        IdempotentCreditStatus status,
        int previousBalance,
        int currentBalance,
        String diagnostic,
        Optional<IdempotentCreditRollback> rollback
) {
    IdempotentCreditResult {
        Objects.requireNonNull(status, "status");
        diagnostic = diagnostic == null ? "" : diagnostic;
        rollback = rollback == null ? Optional.empty() : rollback;
    }
}

record IdempotentCreditRollback(
        int previousBalance,
        int appliedBalance,
        ProgressReceipt receipt
) {
    IdempotentCreditRollback {
        Objects.requireNonNull(receipt, "receipt");
    }
}
