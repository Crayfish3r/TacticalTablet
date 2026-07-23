package com.makar.tacticaltablet.integration.discord;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

public final class SetReportDispatchCoordinator {
    private static final long INITIAL_BACKOFF_TICKS = 20L;
    private static final long MAX_BACKOFF_TICKS = 20L * 60L;

    private UUID activeSetId;
    private boolean inFlight;
    private long attemptToken;
    private int failures;
    private long nextAttemptTick;

    public void request(
            UUID setId,
            LongSupplier tick,
            Sender sender,
            Marker marker,
            Executor serverExecutor
    ) {
        Objects.requireNonNull(setId, "setId");
        Objects.requireNonNull(tick, "tick");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(marker, "marker");
        Objects.requireNonNull(serverExecutor, "serverExecutor");

        long token;
        synchronized (this) {
            if (!setId.equals(activeSetId)) {
                activeSetId = setId;
                inFlight = false;
                failures = 0;
                nextAttemptTick = 0L;
                attemptToken++;
            }
            if (inFlight || tick.getAsLong() < nextAttemptTick) return;
            inFlight = true;
            token = ++attemptToken;
        }

        CompletableFuture<Boolean> future;
        try {
            future = sender.send();
        } catch (RuntimeException exception) {
            serverExecutor.execute(() -> complete(
                    setId, token, false, tick, marker));
            return;
        }
        if (future == null) {
            serverExecutor.execute(() -> complete(
                    setId, token, false, tick, marker));
            return;
        }
        future.whenComplete((sent, error) -> serverExecutor.execute(() -> complete(
                setId, token, error == null && Boolean.TRUE.equals(sent), tick, marker)));
    }

    private void complete(
            UUID setId,
            long token,
            boolean sent,
            LongSupplier tick,
            Marker marker
    ) {
        synchronized (this) {
            if (!setId.equals(activeSetId) || token != attemptToken || !inFlight) return;
        }

        boolean marked = false;
        if (sent) {
            try {
                marked = marker.markDispatched(setId);
            } catch (RuntimeException ignored) {
                marked = false;
            }
        }
        synchronized (this) {
            if (!setId.equals(activeSetId) || token != attemptToken || !inFlight) return;
            inFlight = false;
            if (marked) {
                failures = 0;
                nextAttemptTick = Long.MAX_VALUE;
                return;
            }
            failures++;
            long backoff = Math.min(
                    MAX_BACKOFF_TICKS,
                    INITIAL_BACKOFF_TICKS << Math.min(5, failures - 1)
            );
            nextAttemptTick = tick.getAsLong() + backoff;
        }
    }

    public synchronized void reset() {
        activeSetId = null;
        inFlight = false;
        failures = 0;
        nextAttemptTick = 0L;
        attemptToken++;
    }

    synchronized boolean isInFlight(UUID setId) {
        return setId != null && setId.equals(activeSetId) && inFlight;
    }

    @FunctionalInterface
    public interface Sender {
        CompletableFuture<Boolean> send();
    }

    @FunctionalInterface
    public interface Marker {
        boolean markDispatched(UUID expectedSetId);
    }
}
