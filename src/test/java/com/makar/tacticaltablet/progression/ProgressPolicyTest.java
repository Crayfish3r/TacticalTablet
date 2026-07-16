package com.makar.tacticaltablet.progression;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressPolicyTest {

    private static final ProgressionRules RULES = new ProgressionRules(
            ClassTier.MONSTER.id(),
            ClassTier.MAX_XP,
            List.of(0, 300, 800, 1300, 2000)
    );

    @Test
    void coinMutationsAddDebitClampAndSaturateWithoutNegativeBalances() {
        assertEquals(new ProgressMutationResult(true, 10, 15), ProgressPolicy.changeCoins(10, 5));
        assertEquals(new ProgressMutationResult(true, 10, 6), ProgressPolicy.changeCoins(10, -4));
        assertEquals(new ProgressMutationResult(true, 10, 0), ProgressPolicy.changeCoins(10, -20));
        assertEquals(new ProgressMutationResult(false, 0, 0), ProgressPolicy.changeCoins(-5, 0));
        assertEquals(Integer.MAX_VALUE, ProgressPolicy.changeCoins(Integer.MAX_VALUE - 1, 10).currentValue());
        assertFalse(ProgressPolicy.canAfford(4, 5));
        assertTrue(ProgressPolicy.canAfford(5, 5));
        assertFalse(ProgressPolicy.canAfford(5, -1));
    }

    @Test
    void purchaseDecisionsPreserveExistingFailurePrecedenceAndBalances() {
        assertEquals(new ProgressPurchaseResult(true, ProgressPurchaseResult.Failure.NONE, 100, 50),
                ProgressPolicy.evaluatePurchase(100, 50, false, true));
        assertEquals(ProgressPurchaseResult.Failure.INSUFFICIENT_FUNDS,
                ProgressPolicy.evaluatePurchase(49, 50, false, true).failure());
        assertEquals(ProgressPurchaseResult.Failure.ALREADY_OWNED,
                ProgressPolicy.evaluatePurchase(100, 50, true, true).failure());
        assertEquals(ProgressPurchaseResult.Failure.INVALID_ITEM,
                ProgressPolicy.evaluatePurchase(100, 0, true, false).failure());
    }

    @Test
    void synchronizedPurchaseBoundaryAllowsOnlyOneConcurrentPurchase() throws Exception {
        PurchaseFixture fixture = new PurchaseFixture(500, 500);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ProgressPurchaseResult>> attempts = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    return fixture.purchase();
                }));
            }
            start.countDown();

            int successes = 0;
            for (Future<ProgressPurchaseResult> attempt : attempts) {
                if (attempt.get().successful()) successes++;
            }
            assertEquals(1, successes);
            assertEquals(0, fixture.balance());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void levelCalculationCoversNoUpgradeSingleUpgradeMultipleUpgradeAndMaximum() {
        assertEquals(ClassTier.BASIC.id(), ProgressPolicy.calculateLevel(299, RULES));
        assertEquals(ClassTier.RARE.id(), ProgressPolicy.calculateLevel(300, RULES));
        assertEquals(ClassTier.LEGEND.id(), ProgressPolicy.calculateLevel(1300, RULES));
        assertEquals(ClassTier.MONSTER.id(), ProgressPolicy.calculateLevel(2000, RULES));
        assertEquals(ClassTier.MONSTER.id(), ProgressPolicy.calculateLevel(Integer.MAX_VALUE, RULES));
        assertEquals(ClassTier.BASIC.id(), ProgressPolicy.calculateLevel(-1, RULES));
    }

    @Test
    void normalizationHandlesNullNegativeDuplicateAndValidValues() {
        assertTrue(ProgressPolicy.normalizeNonNegativeValues(null).isEmpty());

        Map<String, Integer> values = new LinkedHashMap<>();
        values.put(" Scout ", 10);
        values.put("scout", 20);
        values.put("negative", -1);
        values.put("null-value", null);
        values.put(" ", 50);

        assertEquals(Map.of("scout", 20, "negative", 0, "null-value", 0),
                ProgressPolicy.normalizeNonNegativeValues(values));
    }

    @Test
    void progressionRulesDefensivelyCopyThresholdsAndRejectInvalidDefinitions() {
        List<Integer> thresholds = new ArrayList<>(List.of(0, 10));
        ProgressionRules rules = new ProgressionRules(1, 10, thresholds);
        thresholds.set(1, 5);

        assertEquals(List.of(0, 10), rules.experienceThresholds());
        assertThrows(UnsupportedOperationException.class, () -> rules.experienceThresholds().add(20));
        assertThrows(IllegalArgumentException.class, () -> new ProgressionRules(1, 10, List.of(0, 11)));
        assertThrows(IllegalArgumentException.class, () -> new ProgressionRules(2, 10, List.of(0, 10)));
    }

    @Test
    void rewardAndMutationResultsRemainValueObjects() {
        assertEquals(new ProgressReward(10, 5, true), new ProgressReward(10, 5, true));
        assertEquals(new ProgressMutationResult(false, 3, 3), new ProgressMutationResult(false, 3, 3));
    }

    private static final class PurchaseFixture {
        private final int price;
        private int balance;
        private boolean owned;

        private PurchaseFixture(int balance, int price) {
            this.balance = balance;
            this.price = price;
        }

        private synchronized ProgressPurchaseResult purchase() {
            ProgressPurchaseResult result = ProgressPolicy.evaluatePurchase(balance, price, owned, true);
            if (result.successful()) {
                balance = result.currentBalance();
                owned = true;
            }
            return result;
        }

        private synchronized int balance() {
            return balance;
        }
    }
}
