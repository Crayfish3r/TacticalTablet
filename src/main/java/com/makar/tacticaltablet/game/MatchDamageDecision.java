package com.makar.tacticaltablet.game;

public final class MatchDamageDecision {
    private MatchDamageDecision() {
    }

    public static Reason classify(
            boolean responsiblePlayerPresent,
            boolean attackerParticipant,
            boolean victimParticipant,
            boolean friendlyFireOrSelfDamage,
            double actualHealthDamage
    ) {
        if (!responsiblePlayerPresent) {
            return Reason.NO_RESPONSIBLE_PLAYER;
        }
        if (!attackerParticipant) {
            return Reason.ATTACKER_NOT_PARTICIPANT;
        }
        if (!victimParticipant) {
            return Reason.VICTIM_NOT_PARTICIPANT;
        }
        if (friendlyFireOrSelfDamage) {
            return Reason.FRIENDLY_FIRE;
        }
        if (actualHealthDamage <= 0.0D) {
            return Reason.ZERO_HEALTH_DAMAGE;
        }
        return Reason.ACCEPTED;
    }

    public enum Reason {
        NO_RESPONSIBLE_PLAYER("no_responsible_player"),
        ATTACKER_NOT_PARTICIPANT("attacker_not_participant"),
        VICTIM_NOT_PARTICIPANT("victim_not_participant"),
        FRIENDLY_FIRE("friendly_fire"),
        ZERO_HEALTH_DAMAGE("zero_health_damage"),
        ACCEPTED("accepted");

        private final String diagnosticName;

        Reason(String diagnosticName) {
            this.diagnosticName = diagnosticName;
        }

        public String diagnosticName() {
            return diagnosticName;
        }
    }
}
