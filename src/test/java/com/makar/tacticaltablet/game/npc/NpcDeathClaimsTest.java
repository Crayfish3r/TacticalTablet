package com.makar.tacticaltablet.game.npc;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NpcDeathClaimsTest {
    @Test void repeatedDeathCallbacksShareOneClaim() {
        var claims = new NpcDeathClaims();
        UUID match = UUID.randomUUID(), victim = UUID.randomUUID();
        assertTrue(claims.claim(match, victim));
        for (int i = 0; i < 20; i++) assertFalse(claims.claim(match, victim));
        assertTrue(claims.claim(match, UUID.randomUUID()));
    }

    @Test void keyContainsMatchAndVictimAndCleanupReleasesSession() {
        var claims = new NpcDeathClaims();
        UUID match = UUID.randomUUID(), victim = UUID.randomUUID();
        assertFalse(claims.claim(null, victim));
        assertFalse(claims.claim(match, null));
        assertTrue(claims.claim(match, victim));
        assertTrue(claims.claim(UUID.randomUUID(), victim));
        claims.clear();
        assertTrue(claims.claim(match, victim));
    }
}
