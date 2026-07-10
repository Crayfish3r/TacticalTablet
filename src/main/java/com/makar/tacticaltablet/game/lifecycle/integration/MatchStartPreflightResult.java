package com.makar.tacticaltablet.game.lifecycle.integration;

import java.util.Objects;

public record MatchStartPreflightResult(
        boolean accepted,
        MatchStartRejectionReason reason,
        String diagnostic
) {
    public MatchStartPreflightResult {
        reason = reason == null ? MatchStartRejectionReason.UNKNOWN : reason;
        diagnostic = normalize(diagnostic);
        if (accepted) {
            reason = MatchStartRejectionReason.NONE;
        }
    }

    public static MatchStartPreflightResult acceptedResult() {
        return new MatchStartPreflightResult(true, MatchStartRejectionReason.NONE, "accepted");
    }

    public static MatchStartPreflightResult rejected(MatchStartRejectionReason reason, String diagnostic) {
        return new MatchStartPreflightResult(false, Objects.requireNonNull(reason, "reason"), diagnostic);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= 256 ? trimmed : trimmed.substring(0, 256);
    }
}
