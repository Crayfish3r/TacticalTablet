package com.makar.tacticaltablet.progression;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressLockScopeConcurrencyTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final ProgressApplicationService APPLICATION = new ProgressApplicationService(
            new ProgressService(new ProgressCatalog(
                    Set.of("scout"),
                    Set.of("scout", "medic"),
                    Map.of("sniper", 50, "dream", 25),
                    Map.of("sniper", ClassTier.RARE.id(), "dream", ClassTier.RARE.id()),
                    Set.of("saboteur"),
                    100
            ))
    );

    @Test
    void blockedSyncForFirstPlayerDoesNotBlockSecondPlayerMutation() throws Exception {
        TestMutableProgressState first = new TestMutableProgressState(100);
        TestMutableProgressState second = new TestMutableProgressState(100);
        CountDownLatch syncEntered = new CountDownLatch(1);
        CountDownLatch releaseSync = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ProgressPurchaseResult> firstFuture = executor.submit(() -> executePurchase(
                    first, "sniper", 1, ignored -> { }, new Effects(
                            save -> { },
                            mode -> {
                                assertFalse(Thread.holdsLock(PlayerProgressManager.class));
                                syncEntered.countDown();
                                await(releaseSync);
                            }
                    )));

            assertTrue(syncEntered.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            Future<ProgressPurchaseResult> secondFuture = executor.submit(() -> executePurchase(
                    second, "sniper", 2, ignored -> { }, Effects.noOp()));

            assertTrue(secondFuture.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).successful());
            assertEquals(50, second.coins());
            assertFalse(firstFuture.isDone());
            releaseSync.countDown();
            assertTrue(firstFuture.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).successful());
            assertEquals(50, first.coins());
        } finally {
            releaseSync.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void blockedResponseForFirstPlayerDoesNotBlockSecondPlayerMutation() throws Exception {
        TestMutableProgressState first = new TestMutableProgressState(100);
        TestMutableProgressState second = new TestMutableProgressState(100);
        CountDownLatch responseEntered = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ProgressPurchaseResult> firstFuture = executor.submit(() -> executePurchase(
                    first,
                    "sniper",
                    1,
                    ignored -> {
                        assertFalse(Thread.holdsLock(PlayerProgressManager.class));
                        responseEntered.countDown();
                        await(releaseResponse);
                    },
                    Effects.noOp()
            ));

            assertTrue(responseEntered.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            Future<ProgressPurchaseResult> secondFuture = executor.submit(() -> executePurchase(
                    second, "sniper", 2, ignored -> { }, Effects.noOp()));

            assertTrue(secondFuture.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).successful());
            assertEquals(50, second.coins());
            releaseResponse.countDown();
            assertTrue(firstFuture.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).successful());
        } finally {
            releaseResponse.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void blockedSaveEnqueueForFirstPlayerDoesNotBlockSecondPlayerMutation() throws Exception {
        TestMutableProgressState first = new TestMutableProgressState(100);
        TestMutableProgressState second = new TestMutableProgressState(100);
        CountDownLatch saveEntered = new CountDownLatch(1);
        CountDownLatch releaseSave = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ProgressPurchaseResult> firstFuture = executor.submit(() -> executePurchase(
                    first, "sniper", 1, ignored -> { }, new Effects(
                            save -> {
                                assertFalse(Thread.holdsLock(PlayerProgressManager.class));
                                saveEntered.countDown();
                                await(releaseSave);
                            },
                            mode -> { }
                    )));

            assertTrue(saveEntered.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
            Future<ProgressPurchaseResult> secondFuture = executor.submit(() -> executePurchase(
                    second, "sniper", 2, ignored -> { }, Effects.noOp()));

            assertTrue(secondFuture.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).successful());
            assertEquals(50, second.coins());
            releaseSave.countDown();
            assertTrue(firstFuture.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).successful());
        } finally {
            releaseSave.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void twoOperationsForOnePlayerKeepMonotonicRevisionsAndImmutableSnapshots() throws Exception {
        TestMutableProgressState state = new TestMutableProgressState(100);
        AtomicLong revisions = new AtomicLong();
        List<QueuedProgressSave> attempted = new CopyOnWriteArrayList<>();
        AtomicLong acceptedRevision = new AtomicLong(Long.MIN_VALUE);
        CountDownLatch firstResponse = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        ProgressApplicationService.SideEffects repository = new Effects(save -> {
            attempted.add(save);
            acceptedRevision.accumulateAndGet(save.snapshot().revision(), Math::max);
        }, mode -> { });
        try {
            Future<ProgressPurchaseResult> first = executor.submit(() -> executePurchase(
                    state,
                    "sniper",
                    revisions.incrementAndGet(),
                    ignored -> {
                        firstResponse.countDown();
                        await(releaseFirst);
                    },
                    repository
            ));
            assertTrue(firstResponse.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));

            Future<ProgressPurchaseResult> second = executor.submit(() -> executePurchase(
                    state, "dream", revisions.incrementAndGet(), ignored -> { }, repository));
            assertTrue(second.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).successful());
            assertEquals(25, state.coins());

            releaseFirst.countDown();
            assertTrue(first.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).successful());
            assertEquals(2, attempted.size());
            assertEquals(2L, acceptedRevision.get());
            assertEquals(2L, revisions.get());

            QueuedProgressSave revisionOne = attempted.stream()
                    .filter(save -> save.snapshot().revision() == 1L)
                    .findFirst().orElseThrow();
            QueuedProgressSave revisionTwo = attempted.stream()
                    .filter(save -> save.snapshot().revision() == 2L)
                    .findFirst().orElseThrow();
            assertEquals(50, revisionOne.snapshot().data().coins());
            assertEquals(25, revisionTwo.snapshot().data().coins());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void sideEffectExceptionsNeverRetainTheProgressMonitor() {
        TestMutableProgressState state = new TestMutableProgressState(100);
        PreparedProgressOperation<ProgressPurchaseResult> responseFailure = preparePurchase(
                state, "sniper", 1);
        assertThrows(IllegalStateException.class, () -> APPLICATION.executePostLockEffects(
                responseFailure,
                ignored -> { throw new IllegalStateException("response"); },
                Effects.noOp()
        ));
        assertFalse(Thread.holdsLock(PlayerProgressManager.class));

        TestMutableProgressState next = new TestMutableProgressState(100);
        PreparedProgressOperation<ProgressPurchaseResult> saveFailure = preparePurchase(next, "sniper", 2);
        assertThrows(IllegalStateException.class, () -> APPLICATION.executePostLockEffects(
                saveFailure,
                ignored -> { },
                new Effects(save -> { throw new IllegalStateException("save"); }, mode -> { })
        ));
        assertFalse(Thread.holdsLock(PlayerProgressManager.class));

        TestMutableProgressState last = new TestMutableProgressState(100);
        PreparedProgressOperation<ProgressPurchaseResult> syncFailure = preparePurchase(last, "sniper", 3);
        assertThrows(IllegalStateException.class, () -> APPLICATION.executePostLockEffects(
                syncFailure,
                ignored -> { },
                new Effects(save -> { }, mode -> { throw new IllegalStateException("sync"); })
        ));
        assertFalse(Thread.holdsLock(PlayerProgressManager.class));

        TestMutableProgressState afterFailures = new TestMutableProgressState(100);
        assertTrue(preparePurchase(afterFailures, "sniper", 4).result().successful());
        assertEquals(50, afterFailures.coins());
        assertEquals(50, last.coins());
    }

    private static ProgressPurchaseResult executePurchase(
            TestMutableProgressState state,
            String classId,
            long revision,
            java.util.function.Consumer<ProgressPurchaseResult> response,
            ProgressApplicationService.SideEffects effects
    ) {
        PreparedProgressOperation<ProgressPurchaseResult> operation = preparePurchase(state, classId, revision);
        return APPLICATION.executePostLockEffects(operation, response, effects);
    }

    private static PreparedProgressOperation<ProgressPurchaseResult> preparePurchase(
            TestMutableProgressState state,
            String classId,
            long revision
    ) {
        return PlayerProgressManager.withProgressLock(() -> {
            assertTrue(Thread.holdsLock(PlayerProgressManager.class));
            ProgressApplicationResult<ProgressPurchaseResult> application =
                    APPLICATION.prepareClassPurchase(state, classId, new ProgressContext(false));
            if (!application.changed()) {
                return PreparedProgressOperation.withoutSave(
                        application.outcome(), ProgressSyncMode.TABLET);
            }
            return PreparedProgressOperation.withSave(
                    application.outcome(), snapshot(revision, state.coins()), ProgressSyncMode.TABLET);
        });
    }

    private static QueuedProgressSave snapshot(long revision, int coins) {
        return new QueuedProgressSave(new ProgressSnapshot(
                "player",
                revision,
                new ProgressSnapshot.Data(
                        11, "Player", "00000000000000000000000000000000",
                        Map.of(), Map.of(), Map.of(),
                        0, 0, 0, 0, coins, 0,
                        false, false,
                        Map.of(), Map.of(), Map.of(), List.of(),
                        1L, 2L
                )
        ));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("Timed out waiting for test latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for test latch", exception);
        }
    }

    private record Effects(
            java.util.function.Consumer<QueuedProgressSave> save,
            java.util.function.Consumer<ProgressSyncMode> synchronization
    ) implements ProgressApplicationService.SideEffects {
        static Effects noOp() {
            return new Effects(ignored -> { }, ignored -> { });
        }

        @Override
        public void enqueueSave(QueuedProgressSave preparedSave) {
            save.accept(preparedSave);
        }

        @Override
        public void sync(ProgressSyncMode mode) {
            synchronization.accept(mode);
        }
    }
}
