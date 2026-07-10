package com.makar.tacticaltablet.storage;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Result of a file write with diagnostics suitable for callers and logs. */
public record FileSaveResult(Status status, Path target, String diagnostic, Optional<Throwable> exception) {

    public enum Status {
        SUCCESS,
        FAILED
    }

    public FileSaveResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(target, "target");
        diagnostic = diagnostic == null ? "" : diagnostic;
        exception = exception == null ? Optional.empty() : exception;
    }

    public static FileSaveResult success(Path target) {
        return new FileSaveResult(Status.SUCCESS, target, "", Optional.empty());
    }

    public static FileSaveResult failure(Path target, String diagnostic, Throwable exception) {
        return new FileSaveResult(Status.FAILED, target, diagnostic, Optional.ofNullable(exception));
    }
}
