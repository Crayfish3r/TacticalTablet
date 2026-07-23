package com.makar.tacticaltablet.integration.discord;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetReportDispatchCoordinatorTest {
    @Test
    void trueMarksOnlyAfterServerExecutorRuns() {
        Harness harness = new Harness();
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        harness.request(future);
        future.complete(true);
        assertEquals(0, harness.marks.get());
        harness.runCallbacks();

        assertEquals(1, harness.marks.get());
        assertFalse(harness.coordinator.isInFlight(harness.setId));
    }

    @Test
    void falseAndExceptionRemainPendingAndRetryWithBackoff() {
        Harness harness = new Harness();
        CompletableFuture<Boolean> first = new CompletableFuture<>();
        harness.request(first);
        first.complete(false);
        harness.runCallbacks();
        assertEquals(0, harness.marks.get());

        harness.request(new CompletableFuture<>());
        assertEquals(1, harness.sends.get());
        harness.tick.addAndGet(20);
        CompletableFuture<Boolean> retry = new CompletableFuture<>();
        harness.request(retry);
        assertEquals(2, harness.sends.get());
        retry.completeExceptionally(new IllegalStateException("webhook unavailable"));
        harness.runCallbacks();
        assertEquals(0, harness.marks.get());
    }

    @Test
    void onlyOneFuturePerSetCanBeInFlight() {
        Harness harness = new Harness();
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        harness.request(future);
        harness.request(new CompletableFuture<>());

        assertEquals(1, harness.sends.get());
        assertTrue(harness.coordinator.isInFlight(harness.setId));
    }

    @Test
    void staleCallbackCannotMarkNewSet() {
        Harness harness = new Harness();
        CompletableFuture<Boolean> old = new CompletableFuture<>();
        harness.request(old);
        UUID newSet = UUID.randomUUID();
        harness.setId = newSet;
        harness.request(new CompletableFuture<>());

        old.complete(true);
        harness.runCallbacks();
        assertEquals(0, harness.marks.get());
        assertTrue(harness.coordinator.isInFlight(newSet));
    }

    @Test
    void failedFlagSaveLeavesReportPending() {
        Harness harness = new Harness();
        harness.markerResult = false;
        CompletableFuture<Boolean> first = new CompletableFuture<>();
        harness.request(first);
        first.complete(true);
        harness.runCallbacks();

        assertEquals(1, harness.marks.get());
        harness.tick.addAndGet(20);
        harness.request(new CompletableFuture<>());
        assertEquals(2, harness.sends.get());
    }

    @Test
    void resetModelsRestartAndAllowsPendingReportToSendAgain() {
        Harness harness = new Harness();
        CompletableFuture<Boolean> beforeRestart = new CompletableFuture<>();
        harness.request(beforeRestart);
        harness.coordinator.reset();

        harness.request(new CompletableFuture<>());
        assertEquals(2, harness.sends.get());
    }

    private static final class Harness {
        private final SetReportDispatchCoordinator coordinator = new SetReportDispatchCoordinator();
        private final AtomicLong tick = new AtomicLong();
        private final AtomicInteger sends = new AtomicInteger();
        private final AtomicInteger marks = new AtomicInteger();
        private final Queue<Runnable> callbacks = new ArrayDeque<>();
        private final Executor executor = callbacks::add;
        private UUID setId = UUID.randomUUID();
        private boolean markerResult = true;

        private void request(CompletableFuture<Boolean> future) {
            coordinator.request(
                    setId,
                    tick::get,
                    () -> {
                        sends.incrementAndGet();
                        return future;
                    },
                    expectedSet -> {
                        marks.incrementAndGet();
                        return markerResult && expectedSet.equals(setId);
                    },
                    executor
            );
        }

        private void runCallbacks() {
            while (!callbacks.isEmpty()) callbacks.remove().run();
        }
    }
}
