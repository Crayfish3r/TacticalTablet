package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchWinnerNormalizerTest {
    @Test
    void lastLivingParticipantSurvivesNormalizationFromPreEndingSnapshot() {
        UUID winnerId = UUID.randomUUID();
        Candidate winner = new Candidate(winnerId, "winner");
        Set<UUID> participantIdsBeforeEnding = Set.of(winnerId);

        List<Candidate> result = MatchWinnerNormalizer.normalize(
                List.of(), winner, participantIdsBeforeEnding, Candidate::id);

        assertEquals(List.of(winner), result);
    }

    @Test
    void teamAndClanWinnerListsKeepAllParticipantsAndRejectOutsiders() {
        Candidate first = candidate("first");
        Candidate second = candidate("second");
        Candidate outsider = candidate("outsider");

        List<Candidate> result = MatchWinnerNormalizer.normalize(
                List.of(first, second, first, outsider),
                null,
                Set.of(first.id(), second.id()),
                Candidate::id
        );

        assertEquals(List.of(first, second), result);
    }

    @Test
    void noWinnerIsReturnedOnlyWhenNoEligibleSurvivorExists() {
        Candidate outsider = candidate("outsider");

        List<Candidate> result = MatchWinnerNormalizer.normalize(
                List.of(outsider), outsider, Set.of(), Candidate::id);

        assertTrue(result.isEmpty());
    }

    private static Candidate candidate(String name) {
        return new Candidate(UUID.randomUUID(), name);
    }

    private record Candidate(UUID id, String name) {
    }
}
