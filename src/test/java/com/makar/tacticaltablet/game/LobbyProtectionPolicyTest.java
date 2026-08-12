package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyProtectionPolicyTest {
    @Test
    void physicalLobbyIsProtectedEvenWithStalePlayingTag() {
        assertTrue(LobbyProtectionPolicy.isProtected(true, false, false));
        assertTrue(LobbyProtectionPolicy.isProtected(true, false, true));
    }

    @Test
    void transitionTagDoesNotProtectAnActivePlayerOutsideLobby() {
        assertTrue(LobbyProtectionPolicy.isProtected(false, true, false));
        assertFalse(LobbyProtectionPolicy.isProtected(false, true, true));
        assertFalse(LobbyProtectionPolicy.isProtected(false, false, true));
    }
}
