package com.makar.tacticaltablet.game.lifecycle;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record MatchContext(
        UUID matchId,
        MatchState state,
        String mapId,
        String modeId,
        String startReason,
        UUID initiatingAdministratorUuid,
        Set<UUID> participantIds,
        Set<String> completedLifecycleSteps,
        Instant createdAt,
        Instant stateEnteredAt,
        MatchFailure failure,
        long revision
) {
    public MatchContext {
        matchId = Objects.requireNonNull(matchId, "matchId");
        state = Objects.requireNonNull(state, "state");
        mapId = normalize(mapId, "unknown-map");
        modeId = normalize(modeId, "unknown-mode");
        startReason = normalize(startReason, "unspecified");
        participantIds = Set.copyOf(new LinkedHashSet<>(
                Objects.requireNonNullElse(participantIds, Set.of())
        ));
        completedLifecycleSteps = Set.copyOf(new LinkedHashSet<>(
                Objects.requireNonNullElse(completedLifecycleSteps, Set.of())
        ));
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        stateEnteredAt = Objects.requireNonNull(stateEnteredAt, "stateEnteredAt");
        if (revision < 0L) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
    }

    public static MatchContext prepared(UUID matchId, MatchStartRequest request, Instant now, long revision) {
        Objects.requireNonNull(request, "request");
        return new MatchContext(
                matchId,
                MatchState.PREPARING,
                request.mapId(),
                request.modeId(),
                request.startReason(),
                request.initiatingAdministratorUuid(),
                request.participantIds(),
                Set.of(),
                now,
                now,
                null,
                revision
        );
    }

    public MatchContext transitionTo(MatchState nextState, Instant now, long nextRevision) {
        return new MatchContext(
                matchId,
                nextState,
                mapId,
                modeId,
                startReason,
                initiatingAdministratorUuid,
                participantIds,
                completedLifecycleSteps,
                createdAt,
                now,
                failure,
                nextRevision
        );
    }

    public MatchContext withFailure(MatchState nextState, MatchFailure nextFailure, Instant now, long nextRevision) {
        return new MatchContext(
                matchId,
                nextState,
                mapId,
                modeId,
                startReason,
                initiatingAdministratorUuid,
                participantIds,
                completedLifecycleSteps,
                createdAt,
                now,
                nextFailure,
                nextRevision
        );
    }

    public MatchContext withCompletedStep(String step, long nextRevision) {
        if (step == null || step.isBlank()) {
            return this;
        }
        LinkedHashSet<String> nextSteps = new LinkedHashSet<>(completedLifecycleSteps);
        if (!nextSteps.add(step.trim())) {
            return this;
        }
        return new MatchContext(
                matchId,
                state,
                mapId,
                modeId,
                startReason,
                initiatingAdministratorUuid,
                participantIds,
                nextSteps,
                createdAt,
                stateEnteredAt,
                failure,
                nextRevision
        );
    }

    public MatchContext withParticipant(UUID playerId, long nextRevision) {
        if (playerId == null || participantIds.contains(playerId)) {
            return this;
        }
        LinkedHashSet<UUID> nextParticipants = new LinkedHashSet<>(participantIds);
        nextParticipants.add(playerId);
        return new MatchContext(
                matchId,
                state,
                mapId,
                modeId,
                startReason,
                initiatingAdministratorUuid,
                nextParticipants,
                completedLifecycleSteps,
                createdAt,
                stateEnteredAt,
                failure,
                nextRevision
        );
    }

    private static String normalize(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return fallback;
        return trimmed.length() <= 128 ? trimmed : trimmed.substring(0, 128);
    }
}
