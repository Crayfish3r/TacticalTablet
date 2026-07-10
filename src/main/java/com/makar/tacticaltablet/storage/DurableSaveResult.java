package com.makar.tacticaltablet.storage;

import java.nio.file.Path;
import java.util.Optional;

/** Terminal outcome for one submitted target revision. */
public record DurableSaveResult(Status status, Path target, long revision, String diagnostic, Optional<Throwable> exception) {
    public enum Status { WRITTEN, SUPERSEDED, STALE_REJECTED, FAILED, QUEUE_REJECTED, EXECUTOR_STOPPED }

    public DurableSaveResult {
        diagnostic = diagnostic == null ? "" : diagnostic;
        exception = exception == null ? Optional.empty() : exception;
    }

    static DurableSaveResult of(Status status, Path target, long revision, String diagnostic, Throwable exception) {
        return new DurableSaveResult(status, target, revision, diagnostic, Optional.ofNullable(exception));
    }
}
