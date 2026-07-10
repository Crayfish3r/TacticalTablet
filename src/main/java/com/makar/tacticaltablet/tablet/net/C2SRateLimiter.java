package com.makar.tacticaltablet.tablet.net;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Server-thread rate limiter using monotonic nanoseconds and per-action budgets. */
public final class C2SRateLimiter {

    public record Budget(int maxActions, long windowNanos) {
        public Budget {
            if (maxActions <= 0 || windowNanos <= 0L) throw new IllegalArgumentException("Invalid rate limit budget");
        }
    }

    private final LongSupplier nanoTime;
    private final Map<Key, Deque<Long>> attempts = new HashMap<>();

    public C2SRateLimiter() {
        this(System::nanoTime);
    }

    public C2SRateLimiter(LongSupplier nanoTime) {
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    public boolean tryAcquire(UUID playerId, String action, Budget budget) {
        if (playerId == null || action == null || action.isBlank() || budget == null) return false;
        long now = nanoTime.getAsLong();
        Key key = new Key(playerId, action);
        Deque<Long> timestamps = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        while (!timestamps.isEmpty() && elapsedAtLeast(now, timestamps.peekFirst(), budget.windowNanos())) {
            timestamps.removeFirst();
        }
        if (timestamps.size() >= budget.maxActions()) return false;
        timestamps.addLast(now);
        return true;
    }

    public void clear(UUID playerId) {
        if (playerId == null) return;
        attempts.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    public void clearAll() {
        attempts.clear();
    }

    public void clearExpired(long maximumWindowNanos) {
        long now = nanoTime.getAsLong();
        attempts.entrySet().removeIf(entry -> {
            Deque<Long> timestamps = entry.getValue();
            while (!timestamps.isEmpty() && elapsedAtLeast(now, timestamps.peekFirst(), maximumWindowNanos)) {
                timestamps.removeFirst();
            }
            return timestamps.isEmpty();
        });
    }

    private record Key(UUID playerId, String action) { }

    /** Correct for the single wrap of a monotonic nanosecond counter when the window is below 2^63. */
    private static boolean elapsedAtLeast(long now, long startedAt, long durationNanos) {
        return Long.compareUnsigned(now - startedAt, durationNanos) >= 0;
    }
}
