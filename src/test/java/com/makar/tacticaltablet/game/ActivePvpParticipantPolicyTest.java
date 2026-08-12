package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivePvpParticipantPolicyTest {
    @Test
    void currentPlayingParticipantDoesNotDependOnTemporaryLivesInitialization() {
        assertTrue(eligible(true, true, false, false, false, false));
    }

    @Test
    void rejectsLateAndOrdinarySpectators() {
        assertFalse(eligible(true, true, true, false, false, false));
        assertFalse(eligible(true, true, false, true, false, false));
    }

    @Test
    void rejectsModeratorEliminatedAndOtherMatchPlayers() {
        assertFalse(eligible(true, true, false, false, true, false));
        assertFalse(eligible(true, true, false, false, false, true));
        assertFalse(eligible(false, true, false, false, false, false));
    }

    @Test
    void requiresWarPlayingTag() {
        assertFalse(eligible(true, false, false, false, false, false));
    }

    @Test
    void runtimeEligibilityRequiresRunningMatchAndPhaseOutsidePhysicalLobby() {
        assertFalse(runtimeEligible(true, true, true));
        assertFalse(runtimeEligible(false, true, false));
        assertFalse(runtimeEligible(true, false, false));
        assertTrue(runtimeEligible(true, true, false));
    }

    private static boolean eligible(
            boolean currentMatch,
            boolean playing,
            boolean late,
            boolean spectator,
            boolean moderator,
            boolean eliminated
    ) {
        return ActivePvpParticipantPolicy.isEligible(
                currentMatch,
                playing,
                late,
                spectator,
                moderator,
                eliminated
        );
    }

    private static boolean runtimeEligible(boolean matchRunning, boolean runningPhase, boolean physicalLobby) {
        return ActivePvpParticipantPolicy.isEligible(
                true,
                true,
                false,
                false,
                false,
                false,
                matchRunning,
                runningPhase,
                physicalLobby
        );
    }
}
