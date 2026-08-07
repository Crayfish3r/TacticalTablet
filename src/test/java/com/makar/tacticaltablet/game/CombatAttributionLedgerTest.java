package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CombatAttributionLedgerTest {
    private final UUID victim = UUID.randomUUID();
    private final UUID attacker = UUID.randomUUID();

    @Test
    void directTaczPvpHitIsEligibleForAttribution() {
        assertEquals(300, CombatAttributionLedger.DEFAULT_ATTRIBUTION_WINDOW_TICKS);
        assertTrue(CombatAttributionLedger.shouldRecord(victim, attacker, true, true, false, 8.0F));
    }

    @Test
    void deadVictimCannotBeRecordedAfterNestedMdcDeathCallback() {
        assertFalse(CombatAttributionLedger.isVictimRecordable(false, true));
        assertFalse(CombatAttributionLedger.isVictimRecordable(false, false));
        assertTrue(CombatAttributionLedger.isVictimRecordable(true, false));
    }

    @Test
    void mdcBleedAndLaterFireCanReuseOnlyAnAcceptedPvpHit() {
        assertTrue(CombatAttributionLedger.shouldRecord(victim, attacker, true, true, false, 1.0F));
        assertFalse(CombatAttributionLedger.shouldRecord(victim, attacker, true, true, false, 0.0F));
    }

    @Test
    void selfTeamKillSpectatorAndLobbyDamageAreNotRecorded() {
        assertFalse(CombatAttributionLedger.shouldRecord(victim, victim, true, true, false, 5.0F));
        assertFalse(CombatAttributionLedger.shouldRecord(victim, attacker, true, true, true, 5.0F));
        assertFalse(CombatAttributionLedger.shouldRecord(victim, attacker, true, false, false, 5.0F));
        assertFalse(CombatAttributionLedger.shouldRecord(victim, attacker, false, true, false, 5.0F));
    }
}
