package com.makar.tacticaltablet.game.lifecycle;

import java.util.Optional;
import java.util.UUID;

public record MatchTransitionResult(
        MatchTransitionStatus status,
        MatchState previousState,
        MatchState currentState,
        Optional<UUID> matchId,
        long revision,
        Optional<String> diagnostic,
        Optional<MatchFailure> failure
) {
    public MatchTransitionResult {
        status = status == null ? MatchTransitionStatus.FAILED : status;
        previousState = previousState == null ? MatchState.IDLE : previousState;
        currentState = currentState == null ? MatchState.IDLE : currentState;
        matchId = matchId == null ? Optional.empty() : matchId;
        diagnostic = diagnostic == null ? Optional.empty() : diagnostic.map(MatchTransitionResult::normalize);
        failure = failure == null ? Optional.empty() : failure;
    }

    public static MatchTransitionResult applied(
            MatchState previous,
            MatchContext context,
            String diagnostic
    ) {
        return new MatchTransitionResult(
                MatchTransitionStatus.APPLIED,
                previous,
                context.state(),
                Optional.of(context.matchId()),
                context.revision(),
                Optional.ofNullable(diagnostic),
                Optional.ofNullable(context.failure())
        );
    }

    public static MatchTransitionResult noOp(MatchLifecycleSnapshot snapshot, String diagnostic) {
        return new MatchTransitionResult(
                MatchTransitionStatus.NO_OP,
                snapshot.state(),
                snapshot.state(),
                snapshot.matchId(),
                snapshot.revision(),
                Optional.ofNullable(diagnostic),
                snapshot.failure()
        );
    }

    public static MatchTransitionResult rejected(MatchLifecycleSnapshot snapshot, String diagnostic) {
        return new MatchTransitionResult(
                MatchTransitionStatus.REJECTED,
                snapshot.state(),
                snapshot.state(),
                snapshot.matchId(),
                snapshot.revision(),
                Optional.ofNullable(diagnostic),
                snapshot.failure()
        );
    }

    private static String normalize(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() <= 256) return trimmed;
        return trimmed.substring(0, 256);
    }
}
