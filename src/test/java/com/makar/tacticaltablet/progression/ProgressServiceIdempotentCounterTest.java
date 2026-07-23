package com.makar.tacticaltablet.progression;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressServiceIdempotentCounterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1234), ZoneOffset.UTC);
    private static final ProgressService SERVICE = new ProgressService(new ProgressCatalog(
            Set.of("scout"), Set.of("scout"), Map.of(), Map.of(), Set.of(), 100));

    @Test
    void initialLateAndReconnectUseOneReceiptPerMatch() {
        TestMutableProgressState state = new TestMutableProgressState(0);
        UUID firstMatch = UUID.randomUUID();
        UUID secondMatch = UUID.randomUUID();

        IdempotentCounterResult initial = record(state, firstMatch);
        IdempotentCounterResult reconnect = record(state, firstMatch);
        IdempotentCounterResult nextMatch = record(state, secondMatch);

        assertEquals(IdempotentCreditStatus.APPLIED, initial.status());
        assertEquals(IdempotentCreditStatus.ALREADY_APPLIED, reconnect.status());
        assertEquals(IdempotentCreditStatus.APPLIED, nextMatch.status());
        assertEquals(2, state.counter(MutableProgressState.Counter.MATCHES_PLAYED));
        assertEquals(2, state.receiptCount());
    }

    @Test
    void persistenceFailureRollbackAllowsExactlyOneRetry() {
        TestMutableProgressState state = new TestMutableProgressState(0);
        UUID matchId = UUID.randomUUID();
        IdempotentCounterResult failedSaveMutation = record(state, matchId);

        assertTrue(SERVICE.rollbackIdempotentCounter(
                state, failedSaveMutation.rollback().orElseThrow()));
        assertEquals(0, state.counter(MutableProgressState.Counter.MATCHES_PLAYED));
        assertEquals(0, state.receiptCount());

        IdempotentCounterResult retry = record(state, matchId);
        IdempotentCounterResult replay = record(state, matchId);
        assertEquals(IdempotentCreditStatus.APPLIED, retry.status());
        assertEquals(IdempotentCreditStatus.ALREADY_APPLIED, replay.status());
        assertEquals(1, state.counter(MutableProgressState.Counter.MATCHES_PLAYED));
        assertEquals(1, state.receiptCount());
    }

    @Test
    void guardedRollbackDoesNotDamageLaterCounterMutation() {
        TestMutableProgressState state = new TestMutableProgressState(0);
        IdempotentCounterRollback rollback = record(state, UUID.randomUUID())
                .rollback().orElseThrow();
        SERVICE.incrementCounter(state, MutableProgressState.Counter.MATCHES_PLAYED, 1);

        assertFalse(SERVICE.rollbackIdempotentCounter(state, rollback));
        assertEquals(2, state.counter(MutableProgressState.Counter.MATCHES_PLAYED));
        assertEquals(1, state.receiptCount());
    }

    private static IdempotentCounterResult record(TestMutableProgressState state, UUID matchId) {
        return SERVICE.incrementCounterIdempotently(
                state,
                MutableProgressState.Counter.MATCHES_PLAYED,
                "matches_played:" + matchId,
                "matches_played",
                CLOCK
        );
    }
}
