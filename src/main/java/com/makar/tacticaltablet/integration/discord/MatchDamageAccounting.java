package com.makar.tacticaltablet.integration.discord;

public final class MatchDamageAccounting {
    private MatchDamageAccounting() {
    }

    public static double actualHealthLost(float healthBefore, float healthAfter) {
        if (healthBefore <= 0.0F) return 0.0D;

        float lost = healthBefore - Math.max(0.0F, healthAfter);
        return Math.max(0.0D, Math.min(healthBefore, lost));
    }

    public static double actualHealthLostFromFinalDamage(float healthBefore, float finalHealthDamage) {
        if (healthBefore <= 0.0F || finalHealthDamage <= 0.0F) return 0.0D;

        return Math.max(0.0D, Math.min(healthBefore, finalHealthDamage));
    }

    public static double actualHealthLostFromIncomingDamage(
            float healthBefore,
            float absorptionBefore,
            float finalIncomingDamage
    ) {
        if (healthBefore <= 0.0F || finalIncomingDamage <= 0.0F) return 0.0D;

        float healthDamage = Math.max(0.0F, finalIncomingDamage - Math.max(0.0F, absorptionBefore));
        return Math.max(0.0D, Math.min(healthBefore, healthDamage));
    }

    public static boolean shouldRecordDamage(
            boolean eventCanceled,
            boolean friendlyFire,
            boolean attackerParticipant,
            boolean victimParticipant,
            double actualHealthLost
    ) {
        return !eventCanceled
                && !friendlyFire
                && attackerParticipant
                && victimParticipant
                && actualHealthLost > 0.0D;
    }
}
