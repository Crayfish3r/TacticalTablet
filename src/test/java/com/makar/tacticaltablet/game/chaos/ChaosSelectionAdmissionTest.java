package com.makar.tacticaltablet.game.chaos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChaosSelectionAdmissionTest {
    @Test
    void selectionRequiresCompletedLobbySurvivalTransition() {
        assertTrue(ChaosSelectionAdmission.canSelect(true, true, true, true));
        assertFalse(ChaosSelectionAdmission.canSelect(true, true, true, false));
        assertFalse(ChaosSelectionAdmission.canSelect(true, true, false, true));
        assertFalse(ChaosSelectionAdmission.canSelect(true, false, true, true));
        assertFalse(ChaosSelectionAdmission.canSelect(false, true, true, true));
    }
}
