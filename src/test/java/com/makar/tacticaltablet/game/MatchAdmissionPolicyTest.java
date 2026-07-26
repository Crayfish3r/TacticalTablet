package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchAdmissionPolicyTest {

    @Test
    void newPlayerIsAdmittedWhileWindowIsOpen() {
        assertEquals(MatchAdmissionStatus.ADMITTED, classify(false, true));
    }

    @Test
    void newPlayerBecomesLateWhenWindowCloses() {
        assertEquals(MatchAdmissionStatus.LATE_SPECTATOR, classify(false, false));
    }

    @Test
    void admittedReconnectRemainsAllowedAfterCutoff() {
        assertEquals(MatchAdmissionStatus.ADMITTED, classify(true, false));
    }

    @Test
    void repeatedLateClassificationIsStable() {
        assertEquals(MatchAdmissionStatus.LATE_SPECTATOR, classify(false, false));
        assertEquals(MatchAdmissionStatus.LATE_SPECTATOR, classify(false, false));
    }

    @Test
    void inactiveMatchDoesNotCreateRestriction() {
        assertEquals(
                MatchAdmissionStatus.NO_ACTIVE_MATCH,
                MatchAdmissionPolicy.classify(false, false, false)
        );
        assertEquals(
                MatchAdmissionStatus.NO_ACTIVE_MATCH,
                MatchAdmissionPolicy.classify(false, true, true)
        );
    }

    @Test
    void ruleIsIndependentOfModeAndZonePacing() {
        for (MatchMode ignored : MatchMode.values()) {
            assertEquals(MatchAdmissionStatus.ADMITTED, classify(false, true));
            assertEquals(MatchAdmissionStatus.LATE_SPECTATOR, classify(false, false));
        }
    }

    private static MatchAdmissionStatus classify(boolean admitted, boolean windowOpen) {
        return MatchAdmissionPolicy.classify(true, admitted, windowOpen);
    }
}
