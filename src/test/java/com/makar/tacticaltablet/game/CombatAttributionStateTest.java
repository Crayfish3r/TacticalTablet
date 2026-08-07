package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatAttributionStateTest {
    @Test
    void killRespawnThenEnvironmentalDeathCannotReusePreviousLifeAttacker() {
        CombatAttributionState state = new CombatAttributionState();
        UUID attackerA = UUID.randomUUID();
        UUID victimB = UUID.randomUUID();
        state.put(victimB, entry(attackerA, 100));

        state.clear(victimB); // LivingDeathEvent commit

        assertTrue(state.findFresh(victimB, 120, 300).isEmpty()); // respawn + fall/lava/zone or /kill
    }

    @Test
    void validDamageAfterRespawnCreatesOnlyNewAttribution() {
        CombatAttributionState state = new CombatAttributionState();
        UUID victim = UUID.randomUUID();
        UUID oldAttacker = UUID.randomUUID();
        UUID newAttacker = UUID.randomUUID();
        state.put(victim, entry(oldAttacker, 100));
        state.clear(victim);
        state.put(victim, entry(newAttacker, 130));

        assertEquals(newAttacker, state.findFresh(victim, 140, 300).orElseThrow().attackerId());
    }

    private static CombatAttributionLedger.Entry entry(UUID attacker, int tick) {
        return new CombatAttributionLedger.Entry(attacker, "attacker", tick,
                "tacz.bullet", "tacz:bullet", UUID.randomUUID(), attacker, "tacz:ak_74");
    }
}
