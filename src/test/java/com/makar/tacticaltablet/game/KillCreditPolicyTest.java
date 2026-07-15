package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.progression.PlayerProgressManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KillCreditPolicyTest {
    @Test
    void normalParticipantKillAwardsTheSingleFiveCoinPath() {
        assertEquals(KillCreditPolicy.Outcome.REWARD,
                KillCreditPolicy.classify(true, true, false, false, false));
        assertEquals(5, PlayerProgressManager.KILL_COIN_REWARD);
    }

    @Test
    void selfKillIsIgnored() {
        assertEquals(KillCreditPolicy.Outcome.IGNORE,
                KillCreditPolicy.classify(true, true, true, false, false));
    }

    @Test
    void teamKillIsClassifiedWithoutNormalReward() {
        assertEquals(KillCreditPolicy.Outcome.TEAM_KILL,
                KillCreditPolicy.classify(true, true, false, false, true));
    }

    @Test
    void nonParticipantKillerIsIgnored() {
        assertEquals(KillCreditPolicy.Outcome.IGNORE,
                KillCreditPolicy.classify(true, false, false, false, false));
    }

    @Test
    void victimOwnedProjectileCannotBeCreditedToAnotherPlayer() {
        assertEquals(KillCreditPolicy.Outcome.IGNORE,
                KillCreditPolicy.classify(true, true, false, true, false));
    }
}
