package com.makar.tacticaltablet.game.lifecycle;

import java.util.Objects;

public record MatchFailure(
        MatchFailureStage stage,
        String message,
        String exceptionType
) {
    public MatchFailure {
        stage = stage == null ? MatchFailureStage.UNKNOWN : stage;
        message = normalize(message);
        exceptionType = normalize(exceptionType);
    }

    public static MatchFailure of(MatchFailureStage stage, String message) {
        return new MatchFailure(stage, message, null);
    }

    public static MatchFailure from(MatchFailureStage stage, Exception exception) {
        Objects.requireNonNull(exception, "exception");
        return new MatchFailure(stage, exception.getMessage(), exception.getClass().getName());
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() <= 256 ? trimmed : trimmed.substring(0, 256);
    }
}
