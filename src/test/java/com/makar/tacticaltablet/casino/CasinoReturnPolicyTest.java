package com.makar.tacticaltablet.casino;

import com.makar.tacticaltablet.game.MatchPhase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CasinoReturnPolicyTest {
    @Test
    void waitsForTheFullGracePeriod() {
        assertEquals(CasinoReturnPolicy.Decision.WAIT, CasinoReturnPolicy.decide(
                false, true, MatchPhase.RUNNING, false, false, false, false
        ));
    }

    @Test
    void releasesPlayerForNextRoundWhenNoRoundIsRunning() {
        assertEquals(CasinoReturnPolicy.Decision.RELEASE_FOR_NEXT_MATCH, CasinoReturnPolicy.decide(
                true, false, MatchPhase.STARTING, false, false, false, false
        ));
        assertEquals(CasinoReturnPolicy.Decision.RELEASE_FOR_NEXT_MATCH, CasinoReturnPolicy.decide(
                true, false, MatchPhase.WAITING, false, false, false, false
        ));
    }

    @Test
    void admitsOnlyEligiblePlayersToAnActuallyRunningRound() {
        assertEquals(CasinoReturnPolicy.Decision.ADMIT_ACTIVE_MATCH, CasinoReturnPolicy.decide(
                true, true, MatchPhase.RUNNING, false, false, false, false
        ));
        assertEquals(CasinoReturnPolicy.Decision.KEEP_SPECTATING, CasinoReturnPolicy.decide(
                true, true, MatchPhase.RUNNING, true, false, false, false
        ));
        assertEquals(CasinoReturnPolicy.Decision.KEEP_SPECTATING, CasinoReturnPolicy.decide(
                true, true, MatchPhase.RUNNING, false, true, false, false
        ));
        assertEquals(CasinoReturnPolicy.Decision.KEEP_SPECTATING, CasinoReturnPolicy.decide(
                true, true, MatchPhase.RUNNING, false, false, true, false
        ));
        assertEquals(CasinoReturnPolicy.Decision.KEEP_SPECTATING, CasinoReturnPolicy.decide(
                true, true, MatchPhase.RUNNING, false, false, false, true
        ));
    }
}