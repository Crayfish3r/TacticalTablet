package com.makar.tacticaltablet.game.set;

import com.makar.tacticaltablet.game.MatchMode;
import com.makar.tacticaltablet.game.team.TeamId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchPlacementTrackerTest {
    @Test
    void soloEliminationOrderProducesActualPlacementsWithoutDoubleAssignment() {
        MatchPlacementTracker tracker = new MatchPlacementTracker();
        tracker.start(MatchMode.SOLO);
        UUID firstOut = id(1), secondOut = id(2), runnerUp = id(3), winner = id(4);
        tracker.register(firstOut, "First", null);
        tracker.register(secondOut, "Second", null);
        tracker.register(runnerUp, "Runner", null);
        tracker.register(winner, "Winner", null);
        tracker.recordPlayerEliminated(firstOut, 10);
        tracker.recordPlayerEliminated(firstOut, 11);
        tracker.recordPlayerEliminated(secondOut, 20);
        tracker.recordPlayerEliminated(runnerUp, 30);

        Map<UUID, Integer> places = placements(tracker.finish(List.of(winner), null, Map.of()));
        assertEquals(1, places.get(winner));
        assertEquals(2, places.get(runnerUp));
        assertEquals(3, places.get(secondOut));
        assertEquals(4, places.get(firstOut));
    }

    @Test
    void teamMembersShareTeamPlacementWhileCombatRemainsIndividual() {
        MatchPlacementTracker tracker = new MatchPlacementTracker();
        tracker.start(MatchMode.DUO);
        UUID a1 = id(1), a2 = id(2), b1 = id(3), b2 = id(4);
        tracker.register(a1, "A1", TeamId.ALFA);
        tracker.register(a2, "A2", TeamId.ALFA);
        tracker.register(b1, "B1", TeamId.BETA);
        tracker.register(b2, "B2", TeamId.BETA);
        tracker.recordPlayerEliminated(a1, 5);
        tracker.recordTeamEliminated(TeamId.BETA, 20);

        Map<UUID, Integer> places = placements(tracker.finish(List.of(a2), TeamId.ALFA, Map.of(
                a1, new MatchPlacementTracker.CombatTotals(9, 2, 100),
                a2, new MatchPlacementTracker.CombatTotals(0, 0, 0))));
        assertEquals(1, places.get(a1));
        assertEquals(1, places.get(a2));
        assertEquals(2, places.get(b1));
        assertEquals(2, places.get(b2));
    }

    @Test
    void simultaneousTeamEliminationUsesCombatThenStableTeamId() {
        MatchPlacementTracker tracker = new MatchPlacementTracker();
        tracker.start(MatchMode.DUO);
        UUID alpha = id(1), beta = id(2), gamma = id(3);
        tracker.register(alpha, "A", TeamId.ALFA);
        tracker.register(beta, "B", TeamId.BETA);
        tracker.register(gamma, "G", TeamId.GAMMA);
        tracker.recordTeamEliminated(TeamId.ALFA, 10);
        tracker.recordTeamEliminated(TeamId.BETA, 10);

        Map<UUID, Integer> combatPlaces = placements(tracker.finish(List.of(gamma), TeamId.GAMMA, Map.of(
                alpha, new MatchPlacementTracker.CombatTotals(1, 0, 0),
                beta, new MatchPlacementTracker.CombatTotals(2, 0, 0))));
        assertEquals(2, combatPlaces.get(beta));
        assertEquals(3, combatPlaces.get(alpha));

        MatchPlacementTracker stable = new MatchPlacementTracker();
        stable.start(MatchMode.DUO);
        stable.register(alpha, "A", TeamId.ALFA);
        stable.register(beta, "B", TeamId.BETA);
        stable.register(gamma, "G", TeamId.GAMMA);
        stable.recordTeamEliminated(TeamId.ALFA, 10);
        stable.recordTeamEliminated(TeamId.BETA, 10);
        Map<UUID, Integer> stablePlaces = placements(stable.finish(List.of(gamma), TeamId.GAMMA, Map.of()));
        assertEquals(2, stablePlaces.get(alpha));
        assertEquals(3, stablePlaces.get(beta));
    }

    private static Map<UUID, Integer> placements(MatchPlacementTracker.PlacementSnapshot snapshot) {
        return snapshot.players().stream().collect(java.util.stream.Collectors.toMap(
                MatchPlacementTracker.PlayerPlacement::playerId,
                MatchPlacementTracker.PlayerPlacement::placement));
    }

    private static UUID id(long value) {
        return new UUID(0, value);
    }
}
