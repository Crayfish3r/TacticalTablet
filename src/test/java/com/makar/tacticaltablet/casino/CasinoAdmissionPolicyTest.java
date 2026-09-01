package com.makar.tacticaltablet.casino;

import com.makar.tacticaltablet.game.MatchPhase;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CasinoAdmissionPolicyTest {

    @Test
    void npcEntryIsLimitedToSafeBetweenRoundPhases() {
        EnumSet<MatchPhase> allowed = EnumSet.of(
                MatchPhase.WAITING,
                MatchPhase.VOTING,
                MatchPhase.TEAM_SELECT,
                MatchPhase.POST_GAME,
                MatchPhase.SET_REWARDING
        );
        for (MatchPhase phase : MatchPhase.values()) {
            boolean actual = CasinoAdmissionPolicy.allowsNpc(true, false, false, false, phase);
            if (allowed.contains(phase)) assertTrue(actual, phase.name());
            else assertFalse(actual, phase.name());
        }
        assertFalse(CasinoAdmissionPolicy.allowsNpc(false, false, false, false, MatchPhase.WAITING));
        assertFalse(CasinoAdmissionPolicy.allowsNpc(true, true, false, false, MatchPhase.WAITING));
        assertFalse(CasinoAdmissionPolicy.allowsNpc(true, false, true, false, MatchPhase.WAITING));
        assertFalse(CasinoAdmissionPolicy.allowsNpc(true, false, false, true, MatchPhase.WAITING));
    }

    @Test
    void spectatorEntryRequiresAnActuallyRunningRound() {
        assertTrue(CasinoAdmissionPolicy.allowsSpectator(true, true, false, MatchPhase.RUNNING));
        assertFalse(CasinoAdmissionPolicy.allowsSpectator(false, true, false, MatchPhase.RUNNING));
        assertFalse(CasinoAdmissionPolicy.allowsSpectator(true, false, false, MatchPhase.RUNNING));
        assertFalse(CasinoAdmissionPolicy.allowsSpectator(true, true, true, MatchPhase.RUNNING));
        assertFalse(CasinoAdmissionPolicy.allowsSpectator(true, true, false, MatchPhase.POST_GAME));
    }
}
