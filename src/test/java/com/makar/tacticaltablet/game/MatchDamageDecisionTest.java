package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchDamageDecisionTest {
    @Test
    void ordinaryPvpDamageIsAccepted() {
        assertEquals(MatchDamageDecision.Reason.ACCEPTED,
                classify(true, true, true, false, 4.0D));
    }

    @Test
    void eachRejectionHasTheRequiredDiagnosticReason() {
        assertEquals(MatchDamageDecision.Reason.NO_RESPONSIBLE_PLAYER,
                classify(false, false, true, false, 4.0D));
        assertEquals(MatchDamageDecision.Reason.ATTACKER_NOT_PARTICIPANT,
                classify(true, false, true, false, 4.0D));
        assertEquals(MatchDamageDecision.Reason.VICTIM_NOT_PARTICIPANT,
                classify(true, true, false, false, 4.0D));
        assertEquals(MatchDamageDecision.Reason.FRIENDLY_FIRE,
                classify(true, true, true, true, 4.0D));
        assertEquals(MatchDamageDecision.Reason.ZERO_HEALTH_DAMAGE,
                classify(true, true, true, false, 0.0D));
    }

    @Test
    void diagnosticNamesAreStable() {
        assertEquals("no_responsible_player",
                MatchDamageDecision.Reason.NO_RESPONSIBLE_PLAYER.diagnosticName());
        assertEquals("attacker_not_participant",
                MatchDamageDecision.Reason.ATTACKER_NOT_PARTICIPANT.diagnosticName());
        assertEquals("victim_not_participant",
                MatchDamageDecision.Reason.VICTIM_NOT_PARTICIPANT.diagnosticName());
        assertEquals("friendly_fire",
                MatchDamageDecision.Reason.FRIENDLY_FIRE.diagnosticName());
        assertEquals("zero_health_damage",
                MatchDamageDecision.Reason.ZERO_HEALTH_DAMAGE.diagnosticName());
        assertEquals("accepted",
                MatchDamageDecision.Reason.ACCEPTED.diagnosticName());
    }

    private static MatchDamageDecision.Reason classify(
            boolean responsible,
            boolean attackerParticipant,
            boolean victimParticipant,
            boolean friendly,
            double damage
    ) {
        return MatchDamageDecision.classify(
                responsible,
                attackerParticipant,
                victimParticipant,
                friendly,
                damage
        );
    }
}
