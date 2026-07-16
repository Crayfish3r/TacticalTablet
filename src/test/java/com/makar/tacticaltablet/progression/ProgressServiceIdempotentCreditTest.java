package com.makar.tacticaltablet.progression;

import com.makar.tacticaltablet.clan.transaction.RepositoryResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressServiceIdempotentCreditTest {
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1234), ZoneOffset.UTC);
    private static final ProgressService SERVICE = new ProgressService(new ProgressCatalog(
            Set.of("scout"), Set.of("scout"), Map.of(), Map.of(), Set.of(), 100));

    @Test
    void firstReceiptAppliesAndExactReplayIsIdempotent() {
        TestMutableProgressState state = new TestMutableProgressState(10);

        IdempotentCreditResult first = SERVICE.applyIdempotentCredit(
                state, "receipt-1", "set_reward", 5, CLOCK);
        IdempotentCreditResult replay = SERVICE.applyIdempotentCredit(
                state, "receipt-1", "set_reward", 5, CLOCK);

        assertEquals(IdempotentCreditStatus.APPLIED, first.status());
        assertEquals(10, first.previousBalance());
        assertEquals(15, first.currentBalance());
        assertTrue(first.rollback().isPresent());
        assertEquals(IdempotentCreditStatus.ALREADY_APPLIED, replay.status());
        assertEquals(15, state.coins());
        assertEquals(1, state.receiptCount());
    }

    @Test
    void distinctReceiptsCreditIndependentlyAndMismatchConflicts() {
        TestMutableProgressState state = new TestMutableProgressState(10);

        SERVICE.applyIdempotentCredit(state, "receipt-1", "set_reward", 5, CLOCK);
        SERVICE.applyIdempotentCredit(state, "receipt-2", "set_reward", 7, CLOCK);
        IdempotentCreditResult conflict = SERVICE.applyIdempotentCredit(
                state, "receipt-1", "set_reward", 6, CLOCK);

        assertEquals(22, state.coins());
        assertEquals(2, state.receiptCount());
        assertEquals(IdempotentCreditStatus.CONFLICT, conflict.status());
    }

    @Test
    void failedOrOverflowingCreditsNeverWriteReceipt() {
        TestMutableProgressState invalid = new TestMutableProgressState(10);
        TestMutableProgressState overflow = new TestMutableProgressState(Integer.MAX_VALUE);

        assertEquals(IdempotentCreditStatus.FAILED,
                SERVICE.applyIdempotentCredit(invalid, "receipt", "set_reward", 0, CLOCK).status());
        assertEquals(IdempotentCreditStatus.FAILED,
                SERVICE.applyIdempotentCredit(overflow, "receipt", "set_reward", 1, CLOCK).status());
        assertEquals(0, invalid.receiptCount());
        assertEquals(0, overflow.receiptCount());
        assertEquals(10, invalid.coins());
        assertEquals(Integer.MAX_VALUE, overflow.coins());
    }

    @Test
    void rollbackRestoresBalanceAndLedgerOnlyOnce() {
        TestMutableProgressState state = new TestMutableProgressState(10);
        IdempotentCreditResult result = SERVICE.applyIdempotentCredit(
                state, "receipt", "set_reward", 5, CLOCK);
        IdempotentCreditRollback rollback = result.rollback().orElseThrow();

        assertTrue(SERVICE.rollbackIdempotentCredit(state, rollback));
        assertFalse(SERVICE.rollbackIdempotentCredit(state, rollback));
        assertEquals(10, state.coins());
        assertEquals(0, state.receiptCount());
    }

    @Test
    void rollbackAfterIndependentBalanceMutationDoesNotDamageState() {
        TestMutableProgressState state = new TestMutableProgressState(10);
        IdempotentCreditRollback rollback = SERVICE.applyIdempotentCredit(
                state, "receipt", "set_reward", 5, CLOCK).rollback().orElseThrow();
        SERVICE.addCoins(state, 2);

        assertFalse(SERVICE.rollbackIdempotentCredit(state, rollback));
        assertEquals(17, state.coins());
        assertEquals(1, state.receiptCount());
    }

    @Test
    void typedStatusMapsExactlyToLegacyRepositoryResult() {
        TestMutableProgressState state = new TestMutableProgressState(10);
        IdempotentCreditResult applied = SERVICE.applyIdempotentCredit(
                state, "receipt", "set_reward", 5, CLOCK);
        IdempotentCreditResult replay = SERVICE.applyIdempotentCredit(
                state, "receipt", "set_reward", 5, CLOCK);
        IdempotentCreditResult conflict = SERVICE.applyIdempotentCredit(
                state, "receipt", "set_reward", 6, CLOCK);
        IdempotentCreditResult failed = SERVICE.applyIdempotentCredit(
                state, "", "set_reward", 5, CLOCK);

        assertEquals(RepositoryResult.Status.APPLIED, PlayerProgressManager.mapCreditResult(applied).status());
        assertEquals(RepositoryResult.Status.ALREADY_APPLIED,
                PlayerProgressManager.mapCreditResult(replay).status());
        assertEquals(RepositoryResult.Status.CONFLICT, PlayerProgressManager.mapCreditResult(conflict).status());
        assertEquals(RepositoryResult.Status.FAILED, PlayerProgressManager.mapCreditResult(failed).status());
    }
}
