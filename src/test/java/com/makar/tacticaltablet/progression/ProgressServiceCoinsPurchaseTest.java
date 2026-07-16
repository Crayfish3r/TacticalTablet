package com.makar.tacticaltablet.progression;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressServiceCoinsPurchaseTest {
    private static final ProgressService SERVICE = new ProgressService(new ProgressCatalog(
            Set.of("scout"),
            Set.of("scout", "medic"),
            Map.of("sniper", 50),
            Map.of("sniper", 2),
            Set.of("saboteur"),
            100
    ));

    @Test
    void coinMutationsReportPreviousCurrentAndChangedWhilePreservingLegacyBounds() {
        TestMutableProgressState state = new TestMutableProgressState(10);

        assertEquals(new ProgressMutationResult(true, 10, 15), SERVICE.addCoins(state, 5));
        assertEquals(new ProgressMutationResult(true, 15, 11), SERVICE.addCoins(state, -4));
        assertEquals(new ProgressMutationResult(true, 11, 0), SERVICE.addCoins(state, -20));
        assertEquals(new ProgressMutationResult(false, 0, 0), SERVICE.addCoins(state, 0));
        assertEquals(0, state.coins());

        state.coins(Integer.MAX_VALUE - 1);
        assertEquals(Integer.MAX_VALUE, SERVICE.addCoins(state, 10).currentValue());
        assertEquals(Integer.MAX_VALUE, state.coins());
    }

    @Test
    void setCoinsNormalizesNegativeValuesAndIdentifiesNoOp() {
        TestMutableProgressState state = new TestMutableProgressState(12);

        assertEquals(new ProgressMutationResult(true, 12, 0), SERVICE.setCoins(state, -1));
        assertEquals(new ProgressMutationResult(false, 0, 0), SERVICE.setCoins(state, 0));
    }

    @Test
    void successfulPurchaseChangesBalanceUnlockAndExperienceExactlyOnce() {
        TestMutableProgressState state = new TestMutableProgressState(100);

        ProgressPurchaseResult first = SERVICE.purchaseClass(state, " Sniper ", ProgressContext.standard());
        ProgressPurchaseResult second = SERVICE.purchaseClass(state, "sniper", ProgressContext.standard());

        assertEquals(new ProgressPurchaseResult(true, ProgressPurchaseResult.Failure.NONE, 100, 50), first);
        assertEquals(ProgressPurchaseResult.Failure.ALREADY_OWNED, second.failure());
        assertEquals(50, state.coins());
        assertEquals(new ProgressEntry(true, 1), state.purchase("sniper"));
        assertEquals(new ProgressEntry(true, 0), state.experience("sniper"));
    }

    @Test
    void rejectedPurchasesDoNotMutateState() {
        TestMutableProgressState insufficient = new TestMutableProgressState(49);
        TestMutableProgressState unknown = new TestMutableProgressState(100);
        TestMutableProgressState competitive = new TestMutableProgressState(100);

        assertEquals(ProgressPurchaseResult.Failure.INSUFFICIENT_FUNDS,
                SERVICE.purchaseClass(insufficient, "sniper", ProgressContext.standard()).failure());
        assertEquals(ProgressPurchaseResult.Failure.INVALID_ITEM,
                SERVICE.purchaseClass(unknown, "unknown", ProgressContext.standard()).failure());
        assertEquals(ProgressPurchaseResult.Failure.INVALID_ITEM,
                SERVICE.purchaseClass(competitive, "sniper", ProgressContext.competitive()).failure());

        assertEquals(49, insufficient.coins());
        assertFalse(insufficient.purchase("sniper").present());
        assertEquals(100, unknown.coins());
        assertFalse(unknown.purchase("unknown").present());
        assertEquals(100, competitive.coins());
        assertFalse(competitive.purchase("sniper").present());
    }

    @Test
    void legacyPurchaseMappingIsExplicitAndMatchesServiceResult() {
        TestMutableProgressState state = new TestMutableProgressState(100);

        ProgressPurchaseResult purchased = SERVICE.purchaseClass(state, "sniper", ProgressContext.standard());
        ProgressPurchaseResult alreadyOwned = SERVICE.purchaseClass(state, "sniper", ProgressContext.standard());
        ProgressPurchaseResult invalid = SERVICE.purchaseClass(state, "unknown", ProgressContext.standard());

        assertEquals(PlayerProgressManager.PurchaseResult.PURCHASED,
                PlayerProgressManager.mapPurchaseResult(purchased));
        assertEquals(PlayerProgressManager.PurchaseResult.ALREADY_OWNED,
                PlayerProgressManager.mapPurchaseResult(alreadyOwned));
        assertEquals(PlayerProgressManager.PurchaseResult.NOT_PURCHASABLE,
                PlayerProgressManager.mapPurchaseResult(invalid));

        TestMutableProgressState poor = new TestMutableProgressState(0);
        assertEquals(PlayerProgressManager.PurchaseResult.NOT_ENOUGH_COINS,
                PlayerProgressManager.mapPurchaseResult(
                        SERVICE.purchaseClass(poor, "sniper", ProgressContext.standard())));
    }

    @Test
    void serviceKeepsAggregateStateIsolatedAndDoesNotRetainMutationState() {
        TestMutableProgressState first = new TestMutableProgressState(100);
        TestMutableProgressState second = new TestMutableProgressState(100);

        assertTrue(SERVICE.purchaseClass(first, "sniper", ProgressContext.standard()).successful());
        assertFalse(second.purchase("sniper").present());
        assertEquals(100, second.coins());
    }

    @Test
    void externalSynchronizedBoundaryAllowsOnlyOneConcurrentPurchase() throws Exception {
        TestMutableProgressState state = new TestMutableProgressState(100);
        Object facadeLock = new Object();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ProgressPurchaseResult>> attempts = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    synchronized (facadeLock) {
                        return SERVICE.purchaseClass(state, "sniper", ProgressContext.standard());
                    }
                }));
            }
            start.countDown();

            int successes = 0;
            for (Future<ProgressPurchaseResult> attempt : attempts) {
                if (attempt.get().successful()) successes++;
            }
            assertEquals(1, successes);
            assertEquals(50, state.coins());
            assertEquals(new ProgressEntry(true, 1), state.purchase("sniper"));
        } finally {
            executor.shutdownNow();
        }
    }
}
