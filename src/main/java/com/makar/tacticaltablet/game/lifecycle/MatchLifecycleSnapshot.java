package com.makar.tacticaltablet.game.lifecycle;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record MatchLifecycleSnapshot(
        MatchState state,
        Optional<UUID> matchId,
        Optional<String> mapId,
        Optional<String> modeId,
        Optional<MatchFailure> failure,
        Set<UUID> participantIds,
        Set<String> completedLifecycleSteps,
        long revision,
        Optional<Instant> createdAt,
        Optional<Instant> stateEnteredAt
) {
    public MatchLifecycleSnapshot {
        state = state == null ? MatchState.IDLE : state;
        matchId = matchId == null ? Optional.empty() : matchId;
        mapId = mapId == null ? Optional.empty() : mapId;
        modeId = modeId == null ? Optional.empty() : modeId;
        failure = failure == null ? Optional.empty() : failure;
        participantIds = Set.copyOf(participantIds == null ? Set.of() : participantIds);
        completedLifecycleSteps = Set.copyOf(completedLifecycleSteps == null ? Set.of() : completedLifecycleSteps);
        createdAt = createdAt == null ? Optional.empty() : createdAt;
        stateEnteredAt = stateEnteredAt == null ? Optional.empty() : stateEnteredAt;
    }

    public static MatchLifecycleSnapshot idle(long revision) {
        return new MatchLifecycleSnapshot(
                MatchState.IDLE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Set.of(),
                Set.of(),
                revision,
                Optional.empty(),
                Optional.empty()
        );
    }

    public static MatchLifecycleSnapshot from(MatchContext context) {
        return new MatchLifecycleSnapshot(
                context.state(),
                Optional.of(context.matchId()),
                Optional.of(context.mapId()),
                Optional.of(context.modeId()),
                Optional.ofNullable(context.failure()),
                context.participantIds(),
                context.completedLifecycleSteps(),
                context.revision(),
                Optional.of(context.createdAt()),
                Optional.of(context.stateEnteredAt())
        );
    }
}
