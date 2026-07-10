package com.makar.tacticaltablet.game.lifecycle.integration;

import com.makar.tacticaltablet.game.lifecycle.MatchStartStep;
import com.makar.tacticaltablet.game.lifecycle.MatchState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record MatchStartResult(
        MatchStartStatus status,
        Optional<UUID> matchId,
        MatchState initialState,
        MatchState finalState,
        Optional<MatchStartStep> failedStep,
        MatchStartRejectionReason rejectionReason,
        String diagnostic,
        List<String> warnings,
        List<String> rollbackFailures
) {
    public MatchStartResult {
        status = status == null ? MatchStartStatus.FAILED_REQUIRES_CLEANUP : status;
        matchId = matchId == null ? Optional.empty() : matchId;
        initialState = initialState == null ? MatchState.IDLE : initialState;
        finalState = finalState == null ? MatchState.IDLE : finalState;
        failedStep = failedStep == null ? Optional.empty() : failedStep;
        rejectionReason = rejectionReason == null ? MatchStartRejectionReason.UNKNOWN : rejectionReason;
        diagnostic = normalize(diagnostic);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        rollbackFailures = List.copyOf(rollbackFailures == null ? List.of() : rollbackFailures);
    }

    public boolean started() {
        return status == MatchStartStatus.STARTED;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= 512 ? trimmed : trimmed.substring(0, 512);
    }
}
