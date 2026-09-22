package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetModeRotationPolicyTest {
    @Test
    void chaosRequiresThreeCompletedNonChaosSetsBeforeBecomingAvailableAgain() {
        var state = new SetModeRotationPolicy.RotationState(0, 0);
        assertTrue(available(state, 5).contains(SetGameMode.CHAOS));

        state = SetModeRotationPolicy.recordCompletedSet(state, SetGameMode.CHAOS);
        assertEquals(3, state.chaosCooldownRemaining());
        state = SetModeRotationPolicy.recordCompletedSet(state, SetGameMode.CASUAL);
        assertEquals(2, state.chaosCooldownRemaining());
        state = SetModeRotationPolicy.recordCompletedSet(state, SetGameMode.COMPETITIVE);
        assertEquals(1, state.chaosCooldownRemaining());
        state = SetModeRotationPolicy.recordCompletedSet(state, SetGameMode.CASUAL);

        assertEquals(0, state.chaosCooldownRemaining());
        assertTrue(available(state, 5).contains(SetGameMode.CHAOS));
    }

    @Test
    void competitiveIsUnavailableAfterTwoConsecutiveSetsAndResetsOnOtherModes() {
        var state = new SetModeRotationPolicy.RotationState(0, 0);
        state = SetModeRotationPolicy.recordCompletedSet(state, SetGameMode.COMPETITIVE);
        assertEquals(1, state.consecutiveCompetitiveSets());
        state = SetModeRotationPolicy.recordCompletedSet(state, SetGameMode.COMPETITIVE);
        assertEquals(2, state.consecutiveCompetitiveSets());
        assertFalse(available(state, 20).contains(SetGameMode.COMPETITIVE));

        state = SetModeRotationPolicy.recordCompletedSet(state, SetGameMode.CASUAL);
        assertEquals(0, state.consecutiveCompetitiveSets());
        assertTrue(available(state, 5).contains(SetGameMode.COMPETITIVE));

        state = SetModeRotationPolicy.recordCompletedSet(
                new SetModeRotationPolicy.RotationState(0, 1), SetGameMode.CHAOS);
        assertEquals(0, state.consecutiveCompetitiveSets());
    }

    @Test
    void combinedChaosCompetitiveCompetitiveCasualSequenceKeepsCountersIndependent() {
        var state = new SetModeRotationPolicy.RotationState(0, 0);
        state = SetModeRotationPolicy.recordCompletedSet(state, SetGameMode.CHAOS);
        assertEquals(new SetModeRotationPolicy.RotationState(3, 0), state);
        state = SetModeRotationPolicy.recordCompletedSet(state, SetGameMode.COMPETITIVE);
        assertEquals(new SetModeRotationPolicy.RotationState(2, 1), state);
        state = SetModeRotationPolicy.recordCompletedSet(state, SetGameMode.COMPETITIVE);
        assertEquals(new SetModeRotationPolicy.RotationState(1, 2), state);
        assertEquals(Set.of(SetGameMode.CASUAL), available(state, 5));
        state = SetModeRotationPolicy.recordCompletedSet(state, SetGameMode.CASUAL);
        assertEquals(new SetModeRotationPolicy.RotationState(0, 0), state);
        assertEquals(Set.of(SetGameMode.CASUAL, SetGameMode.CHAOS, SetGameMode.COMPETITIVE),
                available(state, 5));
    }

    @Test
    void casualIsAlwaysAvailableForAllRelevantStatesAndPlayerCounts() {
        for (int cooldown = 0; cooldown <= 3; cooldown++) {
            for (int streak = 0; streak <= 4; streak++) {
                for (int players = 0; players <= 10; players++) {
                    assertTrue(available(
                            new SetModeRotationPolicy.RotationState(cooldown, streak), players)
                            .contains(SetGameMode.CASUAL));
                }
            }
        }
    }

    @Test
    void competitiveRequiresFivePlayersAndAStreakBelowTwo() {
        var state = new SetModeRotationPolicy.RotationState(0, 0);
        assertFalse(available(state, 4).contains(SetGameMode.COMPETITIVE));
        assertTrue(available(state, 5).contains(SetGameMode.COMPETITIVE));
        assertTrue(available(state, 6).contains(SetGameMode.COMPETITIVE));
        assertFalse(available(new SetModeRotationPolicy.RotationState(0, 2), 20)
                .contains(SetGameMode.COMPETITIVE));
    }

    @Test
    void serverAvailabilityRejectsLockedSpecialModesButAcceptsCasual() {
        Set<SetGameMode> available = available(new SetModeRotationPolicy.RotationState(2, 2), 4);
        assertTrue(SetModeRotationPolicy.isAvailable(available, SetGameMode.CASUAL));
        assertFalse(SetModeRotationPolicy.isAvailable(available, SetGameMode.CHAOS));
        assertFalse(SetModeRotationPolicy.isAvailable(available, SetGameMode.COMPETITIVE));
    }

    @Test
    void competitivePreStartFallsBackBelowFiveAndRemainsCompetitiveAtFive() {
        assertEquals(SetGameMode.CASUAL,
                SetModeRotationPolicy.resolveCompetitivePreStart(SetGameMode.COMPETITIVE, 4));
        assertEquals(SetGameMode.COMPETITIVE,
                SetModeRotationPolicy.resolveCompetitivePreStart(SetGameMode.COMPETITIVE, 5));
    }

    @Test
    void fallbackCasualCompletionIsRecordedAsCasual() {
        var before = new SetModeRotationPolicy.RotationState(2, 1);
        var after = SetModeRotationPolicy.recordCompletedSet(before, SetGameMode.CASUAL);
        assertEquals(new SetModeRotationPolicy.RotationState(1, 0), after);
    }

    @Test
    void completionTransitionIsRecordedOnlyOnce() {
        assertTrue(SetModeRotationPolicy.shouldRecordCompletion(4, 5, 5));
        assertFalse(SetModeRotationPolicy.shouldRecordCompletion(5, 5, 5));
        assertFalse(SetModeRotationPolicy.shouldRecordCompletion(5, 6, 5));
    }

    @Test
    void persistedValuesAreNormalizedSafely() {
        assertEquals(new SetModeRotationPolicy.RotationState(0, 0),
                new SetModeRotationPolicy.RotationState(-10, -5));
        assertEquals(new SetModeRotationPolicy.RotationState(3, 7),
                new SetModeRotationPolicy.RotationState(100, 7));
    }

    private static Set<SetGameMode> available(SetModeRotationPolicy.RotationState state, int players) {
        return SetModeRotationPolicy.availableModes(state, players);
    }
}
