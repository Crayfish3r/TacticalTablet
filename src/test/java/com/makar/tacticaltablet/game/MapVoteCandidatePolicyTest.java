package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapVoteCandidatePolicyTest {

    @Test
    void sixMapPoolReturnsThreeUniqueExistingCandidates() {
        List<String> pool = maps(6);

        List<String> candidates = select(pool, List.of());

        assertEquals(3, candidates.size());
        assertEquals(3, new HashSet<>(candidates).size());
        assertTrue(pool.containsAll(candidates));
    }

    @Test
    void differentSeededRandomsCanProduceDifferentCandidateSets() {
        Set<Set<String>> selections = IntStream.range(0, 20)
                .mapToObj(seed -> Set.copyOf(MapVoteCandidatePolicy.selectCandidates(
                        maps(6), List.of(), 3, 3, new Random(seed))))
                .collect(Collectors.toSet());

        assertTrue(selections.size() > 1);
    }

    @Test
    void threeRecentMapsAreExcludedWhenEnoughAlternativesExist() {
        List<String> candidates = select(maps(6), List.of("1", "2", "3"));

        assertEquals(Set.of("4", "5", "6"), Set.copyOf(candidates));
    }

    @Test
    void playedMapBecomesAvailableAfterThreeOtherMaps() {
        List<String> candidates = select(maps(6), List.of("2", "3", "4"));

        assertEquals(Set.of("1", "5", "6"), Set.copyOf(candidates));
    }

    @Test
    void fourMapPoolReleasesOnlyTheTwoOldestBlockedMaps() {
        List<String> candidates = select(maps(4), List.of("1", "2", "3"));

        assertEquals(Set.of("1", "2", "4"), Set.copyOf(candidates));
        assertFalse(candidates.contains("3"));
    }

    @Test
    void fiveMapPoolReleasesOnlyTheOldestBlockedMap() {
        List<String> candidates = select(maps(5), List.of("1", "2", "3"));

        assertEquals(Set.of("1", "4", "5"), Set.copyOf(candidates));
    }

    @Test
    void poolsFromZeroToThreeReturnEveryUniqueAvailableMap() {
        assertEquals(List.of(), select(List.of(), List.of()));
        assertEquals(Set.of("1"), Set.copyOf(select(maps(1), List.of("1"))));
        assertEquals(Set.of("1", "2"), Set.copyOf(select(maps(2), List.of("1", "2"))));
        assertEquals(Set.of("1", "2", "3"), Set.copyOf(select(maps(3), List.of("1", "2", "3"))));
    }

    @Test
    void nullBlankAndCaseInsensitivePoolDuplicatesAreRemoved() {
        List<String> candidates = select(
                java.util.Arrays.asList(" Alpha ", "alpha", null, "", "Bravo", "CHARLIE", "charlie"),
                List.of()
        );

        assertEquals(Set.of("Alpha", "Bravo", "CHARLIE"), Set.copyOf(candidates));
    }

    @Test
    void historyIsCaseInsensitiveCanonicalAndIgnoresRemovedMaps() {
        List<String> normalized = MapVoteCandidatePolicy.normalizeRecentPlayedMaps(
                List.of("Alpha", "Bravo", "Charlie", "Delta"),
                List.of("ALPHA", "removed", "bravo", "alpha", "CHARLIE"),
                3
        );

        assertEquals(List.of("Bravo", "Alpha", "Charlie"), normalized);
        assertEquals(Set.of("Bravo", "Delta", "Alpha"), Set.copyOf(select(
                List.of("Alpha", "Bravo", "Charlie", "Delta"),
                normalized
        )));
    }

    @Test
    void emptyPoolPreservesStoredCooldownAndRestoredPoolReconcilesIt() {
        List<String> history = List.of("Alpha", "Removed", "Bravo");

        List<String> unavailable = MapVoteCandidatePolicy.reconcileRecentPlayedMaps(
                List.of(), history, 3);
        List<String> restored = MapVoteCandidatePolicy.reconcileRecentPlayedMaps(
                List.of("Alpha", "Bravo", "Charlie"), unavailable, 3);

        assertEquals(history, unavailable);
        assertEquals(List.of("Alpha", "Bravo"), restored);
    }

    @Test
    void recordingPlayedMapIsCanonicalBoundedAndIdempotent() {
        List<String> pool = List.of("Alpha", "Bravo", "Charlie", "Delta");

        List<String> once = MapVoteCandidatePolicy.recordPlayedMap(
                pool, List.of("Alpha", "Bravo", "Charlie"), "BRAVO", 3);
        List<String> twice = MapVoteCandidatePolicy.recordPlayedMap(pool, once, "bravo", 3);

        assertEquals(List.of("Alpha", "Charlie", "Bravo"), once);
        assertEquals(once, twice);
    }

    @Test
    void recordingCurrentPlayedMapBlocksItFromNextSixMapVote() {
        List<String> pool = maps(6);
        List<String> history = MapVoteCandidatePolicy.recordPlayedMap(
                pool, List.of(), "1", MapSetManager.RECENT_MAP_COOLDOWN);

        List<String> candidates = select(pool, history);

        assertFalse(candidates.contains("1"));
        assertEquals(3, candidates.size());
    }

    @Test
    void candidateSelectionDoesNotMutateSavedHistory() {
        List<String> history = new java.util.ArrayList<>(List.of("1", "2", "3"));

        select(maps(4), history);

        assertEquals(List.of("1", "2", "3"), history);
    }

    @Test
    void arbitraryOrBlankCandidateNamesAreRejected() {
        List<String> candidates = List.of("Alpha", "Bravo", "Charlie");

        assertEquals("Bravo", MapVoteCandidatePolicy.canonicalMapName(candidates, " bravo "));
        assertNull(MapVoteCandidatePolicy.canonicalMapName(candidates, "Delta"));
        assertNull(MapVoteCandidatePolicy.canonicalMapName(candidates, ""));
    }

    private static List<String> select(List<String> pool, List<String> recent) {
        return MapVoteCandidatePolicy.selectCandidates(
                pool,
                recent,
                MapSetManager.CANDIDATE_COUNT,
                MapSetManager.RECENT_MAP_COOLDOWN,
                new Random(12345L)
        );
    }

    private static List<String> maps(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(Integer::toString)
                .toList();
    }
}
