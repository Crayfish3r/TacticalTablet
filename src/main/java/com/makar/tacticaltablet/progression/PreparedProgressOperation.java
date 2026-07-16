package com.makar.tacticaltablet.progression;

import java.util.Objects;
import java.util.Optional;

/** Immutable result and integration work prepared while the progression monitor is held. */
record PreparedProgressOperation<T>(
        T result,
        Optional<QueuedProgressSave> queuedSave,
        ProgressSyncMode syncMode
) {
    PreparedProgressOperation {
        Objects.requireNonNull(result, "result");
        queuedSave = Objects.requireNonNull(queuedSave, "queuedSave");
        Objects.requireNonNull(syncMode, "syncMode");
    }

    static <T> PreparedProgressOperation<T> withoutSave(T result, ProgressSyncMode syncMode) {
        return new PreparedProgressOperation<>(result, Optional.empty(), syncMode);
    }

    static <T> PreparedProgressOperation<T> withSave(
            T result,
            QueuedProgressSave queuedSave,
            ProgressSyncMode syncMode
    ) {
        return new PreparedProgressOperation<>(result, Optional.of(queuedSave), syncMode);
    }
}

/** Contains only the immutable snapshot that may cross the progression monitor boundary. */
record QueuedProgressSave(ProgressSnapshot snapshot) {
    QueuedProgressSave {
        Objects.requireNonNull(snapshot, "snapshot");
    }
}

enum ProgressSyncMode {
    NONE,
    TABLET
}
