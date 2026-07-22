package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchAdmissionPolicyTest {

    @Test
    void newPlayerIsAdmittedInPhasesOneAndTwo() {
        assertEquals(MatchAdmissionStatus.ADMITTED, classify(false, 1));
        assertEquals(MatchAdmissionStatus.ADMITTED, classify(false, 2));
    }

    @Test
    void phaseThreeTransitionTickImmediatelyClosesAdmission() {
        assertEquals(MatchAdmissionStatus.ADMITTED, classify(false, 2));
        assertEquals(MatchAdmissionStatus.LATE_SPECTATOR, classify(false, 3));
    }

    @Test
    void newPlayerRemainsLateInEveryLaterPhase() {
        for (int phase = 3; phase <= 6; phase++) {
            assertEquals(MatchAdmissionStatus.LATE_SPECTATOR, classify(false, phase));
        }
    }

    @Test
    void admittedReconnectIsAllowedAfterThreshold() {
        assertEquals(MatchAdmissionStatus.ADMITTED, classify(true, 4));
        assertEquals(MatchAdmissionStatus.ADMITTED, classify(true, 6));
    }

    @Test
    void absentUuidStillResolvesAsLateWhenItReconnects() {
        assertEquals(MatchAdmissionStatus.LATE_SPECTATOR, classify(false, 4));
        assertEquals(MatchAdmissionStatus.LATE_SPECTATOR, classify(false, 4));
    }

    @Test
    void inactiveMatchDoesNotCreateARestriction() {
        assertEquals(MatchAdmissionStatus.NO_ACTIVE_MATCH,
                MatchAdmissionPolicy.classify(false, false, 6));
        assertEquals(MatchAdmissionStatus.NO_ACTIVE_MATCH,
                MatchAdmissionPolicy.classify(true, false, 0));
    }

    @Test
    void ruleIsIndependentOfSoloAndTeamMode() {
        for (MatchMode ignored : MatchMode.values()) {
            assertEquals(MatchAdmissionStatus.LATE_SPECTATOR, classify(false, 3));
            assertEquals(MatchAdmissionStatus.ADMITTED, classify(false, 2));
        }
    }

    @Test
    void clanWarUsesTheSameMatchLifecycleBoundary() {
        boolean clanWar = true;
        assertEquals(MatchAdmissionStatus.LATE_SPECTATOR, classify(false, clanWar ? 3 : 3));
        assertEquals(MatchAdmissionStatus.ADMITTED, classify(false, clanWar ? 2 : 2));
    }

    private static MatchAdmissionStatus classify(boolean admitted, int phase) {
        return MatchAdmissionPolicy.classify(true, admitted, phase);
    }
}
