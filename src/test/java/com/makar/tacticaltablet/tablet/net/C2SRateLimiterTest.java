package com.makar.tacticaltablet.tablet.net;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class C2SRateLimiterTest {

    @Test
    void allowsDeniesAndRefillsUsingMonotonicClock() {
        AtomicLong clock = new AtomicLong();
        C2SRateLimiter limiter = new C2SRateLimiter(clock::get);
        C2SRateLimiter.Budget budget = new C2SRateLimiter.Budget(2, 100L);
        UUID player = UUID.randomUUID();

        assertTrue(limiter.tryAcquire(player, "tablet", budget));
        assertTrue(limiter.tryAcquire(player, "tablet", budget));
        assertFalse(limiter.tryAcquire(player, "tablet", budget));
        assertTrue(limiter.tryAcquire(player, "vote", budget));

        clock.addAndGet(100L);
        assertTrue(limiter.tryAcquire(player, "tablet", budget));
    }

    @Test
    void logoutAndExpirationCleanupRemoveOnlyMatchingState() {
        AtomicLong clock = new AtomicLong();
        C2SRateLimiter limiter = new C2SRateLimiter(clock::get);
        C2SRateLimiter.Budget budget = new C2SRateLimiter.Budget(1, 100L);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        limiter.tryAcquire(first, "clan", budget);
        limiter.tryAcquire(second, "clan", budget);

        limiter.clear(first);
        assertTrue(limiter.tryAcquire(first, "clan", budget));
        assertFalse(limiter.tryAcquire(second, "clan", budget));

        clock.addAndGet(101L);
        limiter.clearExpired(100L);
        assertTrue(limiter.tryAcquire(second, "clan", budget));
    }

    @Test
    void refillsAcrossMonotonicClockOverflow() {
        AtomicLong clock = new AtomicLong(Long.MAX_VALUE - 50L);
        C2SRateLimiter limiter = new C2SRateLimiter(clock::get);
        C2SRateLimiter.Budget budget = new C2SRateLimiter.Budget(1, 100L);
        UUID player = UUID.randomUUID();

        assertTrue(limiter.tryAcquire(player, "tablet", budget));
        assertFalse(limiter.tryAcquire(player, "tablet", budget));
        clock.addAndGet(100L);
        assertTrue(limiter.tryAcquire(player, "tablet", budget));
    }
}
