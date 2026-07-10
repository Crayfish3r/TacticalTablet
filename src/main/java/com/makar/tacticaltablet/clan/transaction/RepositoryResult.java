package com.makar.tacticaltablet.clan.transaction;

import java.util.Optional;

public record RepositoryResult(Status status, String diagnostic, Optional<Throwable> exception) {

    public enum Status {
        APPLIED,
        ALREADY_APPLIED,
        CONFLICT,
        FAILED
    }

    public RepositoryResult {
        diagnostic = diagnostic == null ? "" : diagnostic;
        exception = exception == null ? Optional.empty() : exception;
    }

    public static RepositoryResult applied() {
        return new RepositoryResult(Status.APPLIED, "", Optional.empty());
    }

    public static RepositoryResult alreadyApplied() {
        return new RepositoryResult(Status.ALREADY_APPLIED, "", Optional.empty());
    }

    public static RepositoryResult conflict(String diagnostic) {
        return new RepositoryResult(Status.CONFLICT, diagnostic, Optional.empty());
    }

    public static RepositoryResult failed(String diagnostic, Throwable exception) {
        return new RepositoryResult(Status.FAILED, diagnostic, Optional.ofNullable(exception));
    }

    public boolean isAppliedOrAlreadyApplied() {
        return status == Status.APPLIED || status == Status.ALREADY_APPLIED;
    }
}
