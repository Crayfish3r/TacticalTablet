package com.makar.tacticaltablet.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassTierProgressionTest {

    @Test
    void v10MigrationPreservesExistingTierAndXpValues() {
        assertMigration(0, 0);
        assertMigration(0, 300);
        assertMigration(1, 300);
        assertMigration(1, 800);
        assertMigration(2, 800);
        assertMigration(2, 801);
    }

    @Test
    void migrationOnlyClampsInvalidValuesAndDoesNotInferTiersFromXp() {
        assertEquals(new PlayerProgressManager.PersistedClassProgress(11, 0, 0),
                PlayerProgressManager.migrateClassProgress(10, -1, -1));
        assertEquals(new PlayerProgressManager.PersistedClassProgress(11, 4, 2000),
                PlayerProgressManager.migrateClassProgress(10, 99, 9999));
        assertEquals(2, PlayerProgressManager.migrateClassProgress(10, 2, 800).tier());
    }

    @Test
    void tierTwoAtEightHundredCanContinueToThirteenHundred() {
        assertEquals(1300, PlayerProgressManager.getXpCapForTier(ClassTier.EPIC.id()));
        assertEquals(PlayerProgressManager.ProgressionResult.NOT_ENOUGH_XP,
                PlayerProgressManager.evaluateTierUpgrade(2, 1299, 1000, 3));
        assertEquals(PlayerProgressManager.ProgressionResult.SUCCESS,
                PlayerProgressManager.evaluateTierUpgrade(2, 1300, 500, 3));
    }

    @Test
    void sequentialUpgradeThresholdsAndCostsAreEnforcedWithoutMutationSideEffects() {
        assertResult(0, 299, 1000, 1, PlayerProgressManager.ProgressionResult.NOT_ENOUGH_XP);
        assertResult(0, 300, 49, 1, PlayerProgressManager.ProgressionResult.NOT_ENOUGH_COINS);
        assertResult(0, 300, 50, 1, PlayerProgressManager.ProgressionResult.SUCCESS);
        assertResult(1, 799, 1000, 2, PlayerProgressManager.ProgressionResult.NOT_ENOUGH_XP);
        assertResult(1, 800, 100, 2, PlayerProgressManager.ProgressionResult.SUCCESS);
        assertResult(2, 1299, 1000, 3, PlayerProgressManager.ProgressionResult.NOT_ENOUGH_XP);
        assertResult(2, 1300, 500, 3, PlayerProgressManager.ProgressionResult.SUCCESS);
        assertResult(3, 1999, 2000, 4, PlayerProgressManager.ProgressionResult.NOT_ENOUGH_XP);
        assertResult(3, 2000, 1000, 4, PlayerProgressManager.ProgressionResult.SUCCESS);
        assertResult(4, 2000, 0, 4, PlayerProgressManager.ProgressionResult.MAX_TIER);
        assertResult(0, 2000, 2000, 2, PlayerProgressManager.ProgressionResult.WRONG_TIER);
        assertResult(0, 2000, 2000, 3, PlayerProgressManager.ProgressionResult.WRONG_TIER);
        assertResult(1, 2000, 2000, 3, PlayerProgressManager.ProgressionResult.WRONG_TIER);
        assertResult(2, 2000, 2000, 4, PlayerProgressManager.ProgressionResult.WRONG_TIER);
    }

    private static void assertMigration(int tier, int xp) {
        assertEquals(new PlayerProgressManager.PersistedClassProgress(11, tier, xp),
                PlayerProgressManager.migrateClassProgress(10, tier, xp));
    }

    private static void assertResult(int tier, int xp, int coins, int target,
                                     PlayerProgressManager.ProgressionResult expected) {
        assertEquals(expected, PlayerProgressManager.evaluateTierUpgrade(tier, xp, coins, target));
    }
}
