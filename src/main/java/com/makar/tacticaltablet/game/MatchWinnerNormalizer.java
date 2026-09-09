package com.makar.tacticaltablet.game;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

final class MatchWinnerNormalizer {
    private MatchWinnerNormalizer() {
    }

    static <T> List<T> normalize(
            List<T> winners,
            T fallbackWinner,
            Set<UUID> participantIds,
            Function<T, UUID> idExtractor
    ) {
        Set<UUID> eligibleIds = participantIds == null ? Set.of() : participantIds;
        Map<UUID, T> result = new LinkedHashMap<>();

        if (winners != null) {
            for (T winner : winners) {
                addIfEligible(result, winner, eligibleIds, idExtractor);
            }
        }
        if (result.isEmpty()) {
            addIfEligible(result, fallbackWinner, eligibleIds, idExtractor);
        }
        return List.copyOf(result.values());
    }

    private static <T> void addIfEligible(
            Map<UUID, T> result,
            T candidate,
            Set<UUID> participantIds,
            Function<T, UUID> idExtractor
    ) {
        if (candidate == null) return;
        UUID candidateId = idExtractor.apply(candidate);
        if (candidateId != null && participantIds.contains(candidateId)) {
            result.putIfAbsent(candidateId, candidate);
        }
    }
}
