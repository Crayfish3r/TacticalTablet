package com.makar.tacticaltablet.game.lives;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LivesMatchStatePolicyTest {

    @Test
    void reconnectToSameMatchPreservesLivesAndEliminationState() {
        UUID matchId = UUID.randomUUID();
        assertTrue(LivesManager.isStateFromCurrentMatch(matchId.toString(), matchId));
    }

    @Test
    void stateFromPreviousMatchIsStale() {
        UUID oldMatchId = UUID.randomUUID();
        UUID currentMatchId = UUID.randomUUID();
        assertFalse(LivesManager.isStateFromCurrentMatch(oldMatchId.toString(), currentMatchId));
        assertFalse(LivesManager.isStateFromCurrentMatch("", currentMatchId));
    }

    @Test
    void completedMatchClearsOfflineEliminationStateOnJoin() {
        UUID oldMatchId = UUID.randomUUID();
        assertTrue(LivesManager.shouldResetMatchStateOnJoin(oldMatchId.toString(), null, true));
    }

    @Test
    void idleJoinWithoutMatchStateNeedsNoReset() {
        assertFalse(LivesManager.shouldResetMatchStateOnJoin("", null, false));
    }

    @Test
    void reconnectToCurrentMatchKeepsPersistentState() {
        UUID matchId = UUID.randomUUID();
        assertFalse(LivesManager.shouldResetMatchStateOnJoin(matchId.toString(), matchId, true));
    }
}
