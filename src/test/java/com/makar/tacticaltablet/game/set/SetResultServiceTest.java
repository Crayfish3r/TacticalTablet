package com.makar.tacticaltablet.game.set;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetResultServiceTest {
    @Test
    void snapshotSumsAllSetGamesAndDoesNotDuplicateAGame() {
        UUID id = UUID.randomUUID();
        Map<UUID, String> participants = Map.of(id, "Player");
        List<GamePerformance> games = List.of(
                game(1, id, "Player", 1, 0, 0, 0, 0),
                game(2, id, "Player", 2, 2, 1, 40, 1),
                game(3, id, "Player", 4, 1, 0, 20, 1),
                game(4, id, "Player", 1, 3, 1, 20, 0),
                game(4, id, "Player", 1, 99, 99, 999, 0),
                game(5, id, "Player", 0, 0, 0, 0, 0));

        SetPlayerResult result = SetResultService.createSnapshot(UUID.randomUUID(), participants, games)
                .orderedResults().get(0);

        assertEquals(56, result.totalScore());
        assertEquals(5, result.games().size());
    }

    @Test
    void missingGameContributesZeroButPlayerRemainsRanked() {
        UUID id = UUID.randomUUID();
        SetPlayerResult result = SetResultService.createSnapshot(UUID.randomUUID(), Map.of(id, "Offline"),
                List.of(game(1, id, "Offline", 1, 0, 0, 0, 0))).orderedResults().get(0);

        assertEquals(12, result.totalScore());
        assertEquals(List.of(1, 0, 0, 0, 0),
                result.games().stream().map(GamePerformance::placement).toList());
    }

    @Test
    void nonParticipantsAreExcludedAndParticipantCountUsesUniqueUuids() {
        UUID active = UUID.randomUUID();
        UUID late = UUID.randomUUID();
        UUID lobby = UUID.randomUUID();
        Map<UUID, String> participants = new LinkedHashMap<>();
        participants.put(active, "Active");
        participants.put(late, "Late");

        SetLeaderboardSnapshot snapshot = SetResultService.createSnapshot(UUID.randomUUID(), participants, List.of(
                game(1, active, "Active", 1, 0, 0, 0, 0),
                game(2, late, "Late", 2, 1, 0, 20, 1),
                game(1, lobby, "Lobby", 1, 50, 50, 500, 0)));

        assertEquals(2, snapshot.participantCount());
        assertEquals(List.of(active, late), snapshot.orderedResults().stream().map(SetPlayerResult::playerId).toList());
    }

    @Test
    void rewardSummaryUsesScoreOrderAndExistingOneOrThreePlaceRules() {
        Map<UUID, String> participants = new LinkedHashMap<>();
        java.util.ArrayList<GamePerformance> games = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            UUID id = new UUID(0, i + 1);
            participants.put(id, "P" + i);
            games.add(game(1, id, "P" + i, i + 1, 6 - i, 0, 0, 0));
        }

        SetRewardSummary summary = SetResultService.createRewardSummary(
                SetResultService.createSnapshot(UUID.randomUUID(), participants, games));

        assertEquals(35, summary.rewardCoins());
        assertEquals(3, summary.placements().size());
        assertEquals(List.of("P0", "P1", "P2"), summary.placements().stream().map(SetPlacement::playerName).toList());
    }

    @Test
    void legacySummaryWithoutPerPlacePolicyKeepsItsSharedCoinAmount() {
        SetRewardSummary summary = new SetRewardSummary(UUID.randomUUID(), 6, 35, List.of(
                placement(1), placement(2), placement(3)));

        assertEquals(35, summary.rewardCoins());
        assertTrue(summary.usesLegacyEqualPayouts());
        assertEquals(List.of(35, 35, 35), summary.placements().stream()
                .map(placement -> summary.coinsForPlace(placement.place())).toList());
        assertEquals(3, summary.placements().size());
    }

    @Test
    void casualSummariesPersistRequiredPerPlacePayoutsForSixSevenAndEightParticipants() {
        assertEquals(Map.of(1, 58, 2, 31, 3, 16), rewardSummary(6, false).coinsByPlace());
        assertEquals(Map.of(1, 66, 2, 36, 3, 18), rewardSummary(7, false).coinsByPlace());
        assertEquals(Map.of(1, 74, 2, 41, 3, 20), rewardSummary(8, false).coinsByPlace());
    }

    @Test
    void casualSummaryWithOneRewardedPlaceKeepsTheExistingBaseReward() {
        SetRewardSummary summary = rewardSummary(5, false);

        assertEquals(30, summary.rewardCoins());
        assertEquals(Map.of(1, 30), summary.coinsByPlace());
        assertEquals(1, summary.placements().size());
    }

    @Test
    void offlineParticipantsRemainEligibleForSavedPerPlacePayouts() {
        LinkedHashMap<UUID, String> participants = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) participants.put(new UUID(3, i + 1), "Offline" + i);

        SetRewardSummary summary = SetResultService.createRewardSummary(
                SetResultService.createSnapshot(UUID.randomUUID(), participants, List.of()), false);

        assertEquals(3, summary.placements().size());
        assertEquals(Map.of(1, 58, 2, 31, 3, 16), summary.coinsByPlace());
        assertTrue(summary.placements().stream().allMatch(placement -> placement.playerId() != null));
    }

    @Test
    void competitiveSummaryKeepsThePodiumButHasNoFinalCoinReward() {
        Map<UUID, String> participants = new LinkedHashMap<>();
        java.util.ArrayList<GamePerformance> games = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            UUID id = new UUID(1, i + 1);
            participants.put(id, "Competitive" + i);
            games.add(game(1, id, "Competitive" + i, i + 1, 6 - i, 0, 0, 0));
        }

        SetRewardSummary summary = SetResultService.createRewardSummary(
                SetResultService.createSnapshot(UUID.randomUUID(), participants, games), true);

        assertEquals(0, summary.rewardCoins());
        assertEquals(Map.of(1, 0, 2, 0, 3, 0), summary.coinsByPlace());
        assertEquals(3, summary.placements().size());
        assertEquals(List.of("Competitive0", "Competitive1", "Competitive2"),
                summary.placements().stream().map(SetPlacement::playerName).toList());
    }

    private static GamePerformance game(int number, UUID id, String name, int placement,
                                        int kills, int assists, double damage, int deaths) {
        return new GamePerformance(number, id, name, placement, kills, assists, damage, deaths, "");
    }

    private static SetPlacement placement(int place) {
        return new SetPlacement(place, UUID.randomUUID(), "P" + place, 0, 0, 0, 0, 0.0D, 0);
    }

    private static SetRewardSummary rewardSummary(int participantCount, boolean competitive) {
        Map<UUID, String> participants = new LinkedHashMap<>();
        java.util.ArrayList<GamePerformance> games = new java.util.ArrayList<>();
        for (int i = 0; i < participantCount; i++) {
            UUID id = new UUID(2, i + 1);
            participants.put(id, "P" + i);
            games.add(game(1, id, "P" + i, i + 1, participantCount - i, 0, 0, 0));
        }
        return SetResultService.createRewardSummary(
                SetResultService.createSnapshot(UUID.randomUUID(), participants, games), competitive);
    }
}
