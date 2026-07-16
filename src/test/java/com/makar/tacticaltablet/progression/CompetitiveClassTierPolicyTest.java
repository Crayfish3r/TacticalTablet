package com.makar.tacticaltablet.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompetitiveClassTierPolicyTest {

    @Test
    void mapsTheFiveCompetitiveGamesToTheFiveClassTiers() {
        assertEquals(ClassTier.BASIC, CompetitiveClassTierPolicy.tierForGame(1));
        assertEquals(ClassTier.RARE, CompetitiveClassTierPolicy.tierForGame(2));
        assertEquals(ClassTier.EPIC, CompetitiveClassTierPolicy.tierForGame(3));
        assertEquals(ClassTier.LEGEND, CompetitiveClassTierPolicy.tierForGame(4));
        assertEquals(ClassTier.MONSTER, CompetitiveClassTierPolicy.tierForGame(5));
    }

    @Test
    void clampsRecoveryAndCompletedSetGameNumbersToValidTiers() {
        assertEquals(ClassTier.BASIC, CompetitiveClassTierPolicy.tierForGame(0));
        assertEquals(ClassTier.MONSTER, CompetitiveClassTierPolicy.tierForGame(6));
    }

    @Test
    void competitiveModeUsesTheGameTierInsteadOfTheSavedTier() {
        int savedMonster = ClassTier.MONSTER.id();
        int savedBasic = ClassTier.BASIC.id();

        assertEquals(ClassTier.BASIC.id(), CompetitiveClassTierPolicy.effectiveBaseTier(
                true, false, 1, savedMonster));
        assertEquals(ClassTier.MONSTER.id(), CompetitiveClassTierPolicy.effectiveBaseTier(
                true, false, 5, savedBasic));
        assertEquals(ClassTier.MONSTER.id(), savedMonster);
        assertEquals(ClassTier.BASIC.id(), savedBasic);
    }

    @Test
    void everyPlayerGetsTheSameGameTierAcrossReconnectsRegardlessOfPersonalProfile() {
        int firstConnection = CompetitiveClassTierPolicy.effectiveBaseTier(
                true, false, 4, ClassTier.BASIC.id());
        int reconnect = CompetitiveClassTierPolicy.effectiveBaseTier(
                true, false, 4, ClassTier.MONSTER.id());

        assertEquals(ClassTier.LEGEND.id(), firstConnection);
        assertEquals(firstConnection, reconnect);
    }

    @Test
    void casualAndClanWarModesKeepTheSavedTier() {
        assertEquals(ClassTier.LEGEND.id(), CompetitiveClassTierPolicy.effectiveBaseTier(
                false, false, 1, ClassTier.LEGEND.id()));
        assertEquals(ClassTier.LEGEND.id(), CompetitiveClassTierPolicy.effectiveBaseTier(
                true, true, 5, ClassTier.LEGEND.id()));
        assertEquals(ClassTier.LEGEND.id(), CompetitiveClassTierPolicy.effectiveBaseTier(
                false, true, 5, ClassTier.LEGEND.id()));
    }
}
