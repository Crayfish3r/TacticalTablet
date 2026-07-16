package com.makar.tacticaltablet.game.set;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetRewardDistributionTest {
    @Test
    void requiredExamplesUseLargestRemainderAndPreservePool() {
        assertEquals(Map.of(1, 58, 2, 31, 3, 16), SetRewardDistribution.distribute(35, 3));
        assertEquals(Map.of(1, 66, 2, 36, 3, 18), SetRewardDistribution.distribute(40, 3));
        assertEquals(Map.of(1, 74, 2, 41, 3, 20), SetRewardDistribution.distribute(45, 3));
    }

    @Test
    void zeroPoolAndNoRewardedPlacesAreSupported() {
        assertEquals(Map.of(1, 0, 2, 0, 3, 0), SetRewardDistribution.distribute(0, 3));
        assertEquals(Map.of(), SetRewardDistribution.distribute(0, 0));
    }

    @Test
    void onePlaceKeepsBaseRewardWithoutCreatingAPodiumPool() {
        assertEquals(Map.of(1, 30), SetRewardDistribution.distribute(30, 1));
    }

    @Test
    void rejectsNegativeRewardsAndUnsupportedPlaceCounts() {
        assertThrows(IllegalArgumentException.class, () -> SetRewardDistribution.distribute(-1, 3));
        assertThrows(IllegalArgumentException.class, () -> SetRewardDistribution.distribute(10, 2));
    }

    @Test
    void everySmallPositiveRewardIsOrderedAndConservesTheOldPool() {
        for (int baseReward = 1; baseReward <= 1_000; baseReward++) {
            Map<Integer, Integer> payouts = SetRewardDistribution.distribute(baseReward, 3);
            assertEquals((long) baseReward * 3L, payouts.values().stream().mapToLong(Integer::longValue).sum());
            assertTrue(payouts.get(1) >= payouts.get(2));
            assertTrue(payouts.get(2) >= payouts.get(3));
            assertTrue(payouts.values().stream().allMatch(value -> value >= 0));
        }
    }

    @Test
    void calculationsUseLongBeforeNarrowingIndividualPayouts() {
        Map<Integer, Integer> payouts = SetRewardDistribution.distribute(1_000_000_000, 3);
        assertEquals(3_000_000_000L, payouts.values().stream().mapToLong(Integer::longValue).sum());
        assertEquals(1_650_000_000, payouts.get(1));
        assertThrows(ArithmeticException.class,
                () -> SetRewardDistribution.distribute(Integer.MAX_VALUE, 3));
    }

    @Test
    void returnedDistributionIsImmutable() {
        Map<Integer, Integer> payouts = SetRewardDistribution.distribute(35, 3);
        assertThrows(UnsupportedOperationException.class, () -> payouts.put(1, 0));
    }
}
