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
}
