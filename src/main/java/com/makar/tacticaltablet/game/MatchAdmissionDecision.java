package com.makar.tacticaltablet.game;

import java.util.Optional;
import java.util.UUID;

public record MatchAdmissionDecision(
        MatchAdmissionOutcome outcome,
        Optional<UUID> matchId
) {
    public MatchAdmissionDecision {
        outcome = outcome == null ? MatchAdmissionOutcome.DISCONNECTED : outcome;
        matchId = matchId == null ? Optional.empty() : matchId;
    }
}
