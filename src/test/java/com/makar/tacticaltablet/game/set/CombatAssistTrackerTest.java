package com.makar.tacticaltablet.game.set;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatAssistTrackerTest {
    @Test
    void uniqueRecentAttackerGetsOneAssistAndKillerIsExcluded() {
        CombatAssistTracker tracker = new CombatAssistTracker();
        UUID victim = UUID.randomUUID();
        UUID assister = UUID.randomUUID();
        UUID killer = UUID.randomUUID();
        tracker.recordEffectivePvpDamage(assister, "A", victim, 10);
        tracker.recordEffectivePvpDamage(assister, "A", victim, 20);
        tracker.recordEffectivePvpDamage(killer, "K", victim, 25);

        assertEquals(List.of(assister), tracker.resolveForConfirmedPvpKill(victim, killer, 30).stream()
                .map(CombatAssistTracker.AssistCredit::playerId).toList());
        assertEquals(List.of(), tracker.resolveForConfirmedPvpKill(victim, killer, 30));
    }

    @Test
    void expiredDamageAndNonPvpDeathProduceNoAssist() {
        CombatAssistTracker tracker = new CombatAssistTracker();
        UUID victim = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        tracker.recordEffectivePvpDamage(attacker, "A", victim, 1);
        assertEquals(List.of(), tracker.resolveForConfirmedPvpKill(victim, UUID.randomUUID(),
                2 + SetScoringRules.ASSIST_WINDOW_TICKS));

        tracker.recordEffectivePvpDamage(attacker, "A", victim, 500);
        tracker.clearVictim(victim); // zone/environment death follows the production non-PvP path
        assertEquals(List.of(), tracker.resolveForConfirmedPvpKill(victim, UUID.randomUUID(), 501));
    }

    @Test
    void deathClaimIsIdempotentWithinSameServerTick() {
        CombatAssistTracker tracker = new CombatAssistTracker();
        UUID victim = UUID.randomUUID();
        assertTrue(tracker.claimDeath(victim, 100));
        assertFalse(tracker.claimDeath(victim, 100));
        assertTrue(tracker.claimDeath(victim, 101));
    }
}
