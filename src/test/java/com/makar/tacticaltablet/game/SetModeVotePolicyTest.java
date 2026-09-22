package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetModeVotePolicyTest {
    @Test
    void defaultsToCasualAndRaceIsNeverSelectable() {
        assertEquals(SetGameMode.CASUAL, SetModeVotePolicy.selectWinner(Map.of(), new Random(1)));
        assertFalse(SetGameMode.RACE.selectable());
    }

    @Test
    void specialOperatorModesOverrideOrdinaryVoting() {
        assertTrue(SetModeVotePolicy.ordinaryModesEnabled(false, false));
        assertFalse(SetModeVotePolicy.ordinaryModesEnabled(true, false));
        assertFalse(SetModeVotePolicy.ordinaryModesEnabled(false, true));
    }

    @Test
    void winnerIsSelectedOnlyFromServerAvailableModes() {
        Map<SetGameMode, Integer> counts = Map.of(
                SetGameMode.CASUAL, 1,
                SetGameMode.CHAOS, 100,
                SetGameMode.COMPETITIVE, 100
        );
        assertEquals(SetGameMode.CASUAL, SetModeVotePolicy.selectWinner(
                counts, java.util.Set.of(SetGameMode.CASUAL), new Random(1)));
    }

    @Test
    void existingOrdinalsRemainStableWhenCompetitiveIsAppended() {
        assertEquals(0, SetGameMode.CASUAL.ordinal());
        assertEquals(1, SetGameMode.CHAOS.ordinal());
        assertEquals(2, SetGameMode.RACE.ordinal());
        assertEquals(3, SetGameMode.COMPETITIVE.ordinal());
    }
}
