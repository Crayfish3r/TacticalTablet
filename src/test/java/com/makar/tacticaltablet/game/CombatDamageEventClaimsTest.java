package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatDamageEventClaimsTest {
    @AfterEach
    void resetClaims() {
        CombatDamageEventClaims.reset();
    }

    @Test
    void sameEventCannotBeRecordedTwice() {
        Object event = new Object();

        assertTrue(CombatDamageEventClaims.claim(event));
        assertFalse(CombatDamageEventClaims.claim(event));
    }

    @Test
    void differentEventsRemainIndependent() {
        assertTrue(CombatDamageEventClaims.claim(new Object()));
        assertTrue(CombatDamageEventClaims.claim(new Object()));
    }
}
