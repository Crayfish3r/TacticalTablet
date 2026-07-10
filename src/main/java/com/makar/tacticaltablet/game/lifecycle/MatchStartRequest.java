package com.makar.tacticaltablet.game.lifecycle;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record MatchStartRequest(
        String mapId,
        String modeId,
        String startReason,
        UUID initiatingAdministratorUuid,
        Set<UUID> participantIds,
        Instant requestedAt
) {
    public MatchStartRequest {
        mapId = normalize(mapId, "unknown-map");
        modeId = normalize(modeId, "unknown-mode");
        startReason = normalize(startReason, "unspecified");
        participantIds = Set.copyOf(new LinkedHashSet<>(
                Objects.requireNonNullElse(participantIds, Set.of())
        ));
        requestedAt = requestedAt == null ? Instant.EPOCH : requestedAt;
    }

    private static String normalize(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return fallback;
        return trimmed.length() <= 128 ? trimmed : trimmed.substring(0, 128);
    }
}
