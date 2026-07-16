package com.makar.tacticaltablet.progression;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressServiceExperienceTierTest {
    private static final ProgressionRules RULES = new ProgressionRules(
            ClassTier.MONSTER.id(),
            ClassTier.MAX_XP,
            List.of(0, 300, 800, 1300, 2000)
    );
    private static final ProgressService SERVICE = new ProgressService(new ProgressCatalog(
            Set.of("scout"),
            Set.of("scout", "medic"),
            Map.of("sniper", 50),
            Map.of("sniper", 2),
            Set.of("saboteur"),
            100
    ));

    @Test
    void experienceUsesSavedTierCapButNeverChangesSavedTier() {
        TestMutableProgressState state = new TestMutableProgressState(0);
        state.tier("scout", ClassTier.LEGEND.id());
        state.experience("scout", 200);

        ExperienceMutationResult result = SERVICE.addExperience(state, "scout", 1200, RULES);

        assertEquals(200, result.previousExperience());
        assertEquals(1400, result.currentExperience());
        assertEquals(ClassTier.LEGEND.id(), result.calculatedLevel());
        assertEquals(ClassTier.LEGEND.id(), result.savedTier());
        assertEquals(ClassTier.LEGEND.id(), state.tier("scout").value());
    }

    @Test
    void experienceCoversFirstThresholdMaximumOverflowAndNegativeInput() {
        TestMutableProgressState state = new TestMutableProgressState(0);
        state.tier("scout", ClassTier.MONSTER.id());
        state.experience("scout", 299);

        assertEquals(ClassTier.RARE.id(), SERVICE.addExperience(state, "scout", 1, RULES).calculatedLevel());
        assertEquals(ClassTier.MONSTER.id(),
                SERVICE.addExperience(state, "scout", Integer.MAX_VALUE, RULES).calculatedLevel());
        assertEquals(ClassTier.MAX_XP, state.experience("scout").value());

        ExperienceMutationResult rejected = SERVICE.addExperience(state, "scout", -1, RULES);
        assertFalse(rejected.changed());
        assertEquals(ClassTier.MAX_XP, rejected.currentExperience());
    }

    @Test
    void setExperiencePreservesShopZeroAndNormalizesNegativeValues() {
        TestMutableProgressState state = new TestMutableProgressState(0);
        state.experience("sniper", 20);
        state.experience("scout", 20);

        assertEquals(0, SERVICE.setExperience(state, "sniper", 500, RULES).currentExperience());
        assertEquals(0, SERVICE.setExperience(state, "scout", -1, RULES).currentExperience());
    }

    @Test
    void baseUnlockAppliesCostAndInitialStateOnce() {
        TestMutableProgressState state = new TestMutableProgressState(150);

        BaseUnlockResult first = SERVICE.unlockBaseClass(state, "medic", ProgressContext.standard());
        BaseUnlockResult repeated = SERVICE.unlockBaseClass(state, "medic", ProgressContext.standard());

        assertEquals(ProgressionStatus.SUCCESS, first.status());
        assertEquals(150, first.previousBalance());
        assertEquals(50, first.currentBalance());
        assertTrue(first.changed());
        assertEquals(ProgressionStatus.ALREADY_UNLOCKED, repeated.status());
        assertFalse(repeated.changed());
        assertEquals(new ProgressEntry(true, 1), state.baseUnlock("medic"));
        assertEquals(new ProgressEntry(true, 0), state.experience("medic"));
        assertEquals(new ProgressEntry(true, ClassTier.BASIC.id()), state.tier("medic"));
    }

    @Test
    void baseUnlockRejectionsLeaveProfileUntouched() {
        TestMutableProgressState poor = new TestMutableProgressState(99);
        TestMutableProgressState invalid = new TestMutableProgressState(500);
        TestMutableProgressState competitive = new TestMutableProgressState(500);

        assertEquals(ProgressionStatus.NOT_ENOUGH_COINS,
                SERVICE.unlockBaseClass(poor, "medic", ProgressContext.standard()).status());
        assertEquals(ProgressionStatus.INVALID_CLASS,
                SERVICE.unlockBaseClass(invalid, "unknown", ProgressContext.standard()).status());
        assertEquals(ProgressionStatus.ALREADY_UNLOCKED,
                SERVICE.unlockBaseClass(competitive, "medic", ProgressContext.competitive()).status());
        assertEquals(99, poor.coins());
        assertFalse(poor.baseUnlock("medic").present());
        assertEquals(500, invalid.coins());
        assertEquals(500, competitive.coins());
    }

    @Test
    void tierUpgradePreservesXpAndAppliesOnlyOneTierAndCost() {
        TestMutableProgressState state = unlockedMedic(500, 300, ClassTier.BASIC.id());

        TierUpgradeResult result = SERVICE.upgradeTier(
                state, "medic", ClassTier.RARE.id(), ProgressContext.standard());
        TierUpgradeResult repeated = SERVICE.upgradeTier(
                state, "medic", ClassTier.RARE.id(), ProgressContext.standard());

        assertEquals(ProgressionStatus.SUCCESS, result.status());
        assertEquals(450, state.coins());
        assertEquals(ClassTier.RARE.id(), state.tier("medic").value());
        assertEquals(300, state.experience("medic").value());
        assertEquals(ProgressionStatus.WRONG_TIER, repeated.status());
        assertEquals(450, state.coins());
    }

    @Test
    void tierUpgradeRejectionsAndMaximumDoNotMutateProfile() {
        TestMutableProgressState lowXp = unlockedMedic(500, 299, ClassTier.BASIC.id());
        TestMutableProgressState lowCoins = unlockedMedic(49, 300, ClassTier.BASIC.id());
        TestMutableProgressState maximum = unlockedMedic(5000, 2000, ClassTier.MONSTER.id());
        TestMutableProgressState competitive = unlockedMedic(500, 300, ClassTier.BASIC.id());

        assertEquals(ProgressionStatus.NOT_ENOUGH_XP,
                SERVICE.upgradeTier(lowXp, "medic", ClassTier.RARE.id(), ProgressContext.standard()).status());
        assertEquals(ProgressionStatus.NOT_ENOUGH_COINS,
                SERVICE.upgradeTier(lowCoins, "medic", ClassTier.RARE.id(), ProgressContext.standard()).status());
        assertEquals(ProgressionStatus.MAX_TIER,
                SERVICE.upgradeTier(maximum, "medic", ClassTier.MONSTER.id(), ProgressContext.standard()).status());
        assertEquals(ProgressionStatus.WRONG_TIER,
                SERVICE.upgradeTier(competitive, "medic", ClassTier.RARE.id(), ProgressContext.competitive()).status());
        assertEquals(500, lowXp.coins());
        assertEquals(49, lowCoins.coins());
        assertEquals(5000, maximum.coins());
        assertEquals(500, competitive.coins());
    }

    @Test
    void progressionStatusMappingMaintainsLegacyResultParity() {
        for (ProgressionStatus status : ProgressionStatus.values()) {
            assertEquals(status.name(), PlayerProgressManager.mapProgressionStatus(status).name());
        }
    }

    private static TestMutableProgressState unlockedMedic(int coins, int xp, int tier) {
        TestMutableProgressState state = new TestMutableProgressState(coins);
        state.baseUnlock("medic", 1);
        state.experience("medic", xp);
        state.tier("medic", tier);
        return state;
    }
}
