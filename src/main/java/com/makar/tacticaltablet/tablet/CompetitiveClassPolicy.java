package com.makar.tacticaltablet.tablet;

/** Pure availability policy shared by the authoritative server path and tablet presentation. */
public final class CompetitiveClassPolicy {
    private CompetitiveClassPolicy() {
    }

    public static boolean isAllowed(boolean competitiveSet, ClassCategory category) {
        return !competitiveSet || category != ClassCategory.EXCLUSIVE;
    }

    public static boolean isVipBlocked(boolean competitiveSet, String classKey) {
        return ClassDefinitions.byClassKey(classKey)
                .map(definition -> !isAllowed(competitiveSet, definition.category()))
                .orElse(false);
    }
}
