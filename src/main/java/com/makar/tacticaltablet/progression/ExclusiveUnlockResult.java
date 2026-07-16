package com.makar.tacticaltablet.progression;

import java.util.Objects;
import java.util.Optional;

record ExclusiveUnlockResult(
        Status status,
        boolean changed,
        Optional<ExclusiveUnlockRollback> rollback
) {
    ExclusiveUnlockResult {
        Objects.requireNonNull(status, "status");
        rollback = rollback == null ? Optional.empty() : rollback;
    }

    enum Status {
        GRANTED,
        ALREADY_OWNED,
        INVALID_CLASS
    }
}

record ExclusiveUnlockRollback(
        String classId,
        ProgressEntry previousPurchase,
        ProgressEntry previousExperience
) {
    ExclusiveUnlockRollback {
        Objects.requireNonNull(classId, "classId");
        Objects.requireNonNull(previousPurchase, "previousPurchase");
        Objects.requireNonNull(previousExperience, "previousExperience");
    }
}
