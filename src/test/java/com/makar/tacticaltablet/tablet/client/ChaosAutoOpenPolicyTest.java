package com.makar.tacticaltablet.tablet.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChaosAutoOpenPolicyTest {
    @Test
    void requiresLobbyAndSynchronizedTablet() {
        assertFalse(ChaosAutoOpenPolicy.shouldOpen(true, false, false, true, false, false));
        assertFalse(ChaosAutoOpenPolicy.shouldOpen(true, false, true, false, true, false));
        assertFalse(ChaosAutoOpenPolicy.shouldOpen(true, false, true, true, false, false));
        assertTrue(ChaosAutoOpenPolicy.shouldOpen(true, false, true, true, true, false));
    }

    @Test
    void deathOverlayAndExistingTabletScreenPreventOpening() {
        assertFalse(ChaosAutoOpenPolicy.shouldOpen(true, true, true, true, true, false));
        assertFalse(ChaosAutoOpenPolicy.shouldOpen(true, false, true, true, true, true));
        assertFalse(ChaosAutoOpenPolicy.shouldOpen(false, false, true, true, true, false));
    }
}
