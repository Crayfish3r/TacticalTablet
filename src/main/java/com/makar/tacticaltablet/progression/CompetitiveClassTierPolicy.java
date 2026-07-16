package com.makar.tacticaltablet.progression;

/** Pure policy for the temporary class tier used by a competitive map set. */
public final class CompetitiveClassTierPolicy {

    private CompetitiveClassTierPolicy() {
    }

    public static boolean isActive(boolean competitiveSet, boolean clanWarSet) {
        return competitiveSet && !clanWarSet;
    }

    public static ClassTier tierForGame(int gameNumber) {
        return ClassTier.clamp(gameNumber - 1);
    }

    public static int effectiveBaseTier(
            boolean competitiveSet,
            boolean clanWarSet,
            int gameNumber,
            int standardTier
    ) {
        return isActive(competitiveSet, clanWarSet)
                ? tierForGame(gameNumber).id()
                : ClassTier.clamp(standardTier).id();
    }
}
