package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.core.TacticalTabletMod;

import java.util.EnumMap;
import java.util.Map;

public final class CombatAttributionDiagnostics {
    public static final String ENABLE_PROPERTY =
            "tacticaltablet.debugCombatAttribution";
    private static final Map<MatchDamageDecision.Reason, Long> COUNTERS =
            new EnumMap<>(MatchDamageDecision.Reason.class);

    private CombatAttributionDiagnostics() {
    }

    public static void record(MatchDamageDecision.Reason reason) {
        if (!enabled() || reason == null) {
            return;
        }
        COUNTERS.merge(reason, 1L, Long::sum);
    }

    public static void startMatch() {
        COUNTERS.clear();
    }

    public static void finishMatch() {
        if (enabled()) {
            TacticalTabletMod.LOGGER.info(
                    "[combat-attribution-summary] {}",
                    summary()
            );
        }
        COUNTERS.clear();
    }

    static String summary() {
        StringBuilder result = new StringBuilder();
        for (MatchDamageDecision.Reason reason : MatchDamageDecision.Reason.values()) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(reason.diagnosticName())
                    .append('=')
                    .append(COUNTERS.getOrDefault(reason, 0L));
        }
        return result.toString();
    }

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }
}
