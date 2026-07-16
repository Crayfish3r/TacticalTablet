package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.game.set.SetLeaderboardSnapshot;
import com.makar.tacticaltablet.game.set.SetRewardSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapSetManagerStateMigrationTest {

    @Test
    void legacyRewardedSetKeepsIdentityParticipantsAndRewardState() {
        UUID setId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        SetRewardSummary summary = new SetRewardSummary(setId, 1, 100, List.of());
        SetLeaderboardSnapshot leaderboard = new SetLeaderboardSnapshot(setId, 1, List.of());
        MapSetManager.SetState state = legacyState(4);
        state.setId = setId.toString();
        state.participants.put(playerId.toString(), "Player");
        state.rewardSummary = summary;
        state.leaderboardSnapshot = leaderboard;
        state.rewardEndsAtMillis = -1L;
        state.rewardPhaseStatus = MapSetManager.RewardPhaseStatus.COMPLETED;
        state.setReportDispatched = true;

        MapSetManager.normalizeState(state);
        MapSetManager.normalizeState(state);

        assertEquals(5, state.dataVersion);
        assertEquals(5, state.completedGames);
        assertEquals(setId.toString(), state.setId);
        assertEquals("Player", state.participants.get(playerId.toString()));
        assertSame(summary, state.rewardSummary);
        assertSame(leaderboard, state.leaderboardSnapshot);
        assertEquals(MapSetManager.RewardPhaseStatus.COMPLETED, state.rewardPhaseStatus);
        assertTrue(state.setReportDispatched);
    }

    @Test
    void activeRewardCountdownRestartKeepsTheSavedPerPlaceSummaryAndDeadline() {
        UUID setId = UUID.randomUUID();
        SetRewardSummary summary = SetRewardSummary.withPerPlacePayouts(setId, 6, 35, List.of(),
                Map.of(1, 58, 2, 31, 3, 16));
        MapSetManager.SetState state = legacyState(5);
        long deadline = System.currentTimeMillis() + 10_000L;
        state.setId = setId.toString();
        state.rewardSummary = summary;
        state.rewardEndsAtMillis = deadline;
        state.rewardPhaseStatus = MapSetManager.RewardPhaseStatus.ACTIVE;

        MapSetManager.normalizeState(state);
        MapSetManager.normalizeState(state);

        assertSame(summary, state.rewardSummary);
        assertEquals(deadline, state.rewardEndsAtMillis);
        assertEquals(MapSetManager.RewardPhaseStatus.ACTIVE, state.rewardPhaseStatus);
    }

    private static MapSetManager.SetState legacyState(int completedGames) {
        MapSetManager.SetState state = new MapSetManager.SetState();
        state.dataVersion = 4;
        state.completedGames = completedGames;
        return state;
    }
}
