package com.makar.tacticaltablet.progression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.makar.tacticaltablet.clan.transaction.ClanCreationPayload;
import com.makar.tacticaltablet.clan.transaction.CreateClanTransaction;
import com.makar.tacticaltablet.clan.transaction.RepositoryResult;
import com.makar.tacticaltablet.clan.transaction.TransactionState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerTransactionReceiptLedgerTest {
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC);
    private static final Gson GSON = new GsonBuilder().create();

    @Test
    void missingReceiptsNormalizeToEmptyLedger() {
        TestState state = new TestState(250, null);

        state.receipts(PlayerTransactionReceiptLedger.normalizeReceipts(state.receipts()));

        assertTrue(state.receipts().isEmpty());
    }

    @Test
    void receiptSerializesAndLoadsWithBalanceInSameSnapshot() {
        TestState state = new TestState(250, new ArrayList<>());
        CreateClanTransaction transaction = transaction(UUID.fromString("22222222-2222-2222-2222-222222222222"), 250, 150);

        RepositoryResult result = PlayerTransactionReceiptLedger.applyDebit(state, transaction, CLOCK, () -> true);
        String json = GSON.toJson(state);
        TestState loaded = GSON.fromJson(json, TestState.class);
        loaded.receipts(PlayerTransactionReceiptLedger.normalizeReceipts(loaded.receipts()));

        assertEquals(RepositoryResult.Status.APPLIED, result.status());
        assertEquals(150, loaded.coins());
        assertEquals(1, loaded.receipts().size());
        assertEquals(transaction.transactionId().toString(), loaded.receipts().get(0).transactionId);
    }

    @Test
    void matchingReceiptReturnsAlreadyApplied() {
        TestState state = new TestState(150, new ArrayList<>());
        CreateClanTransaction transaction = transaction(UUID.fromString("22222222-2222-2222-2222-222222222222"), 250, 150);
        state.receipts().add(PlayerTransactionReceiptLedger.receiptFor(transaction, CLOCK.millis()));

        assertEquals(RepositoryResult.Status.ALREADY_APPLIED,
                PlayerTransactionReceiptLedger.applyDebit(state, transaction, CLOCK, () -> true).status());
    }

    @Test
    void sameTransactionIdWithDifferentAmountsOperationOrPayloadConflicts() {
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        CreateClanTransaction transaction = transaction(id, 250, 150);

        assertConflictWithMutatedReceipt(transaction, receipt -> receipt.expectedOldBalance = 251);
        assertConflictWithMutatedReceipt(transaction, receipt -> receipt.newBalance = 149);
        assertConflictWithMutatedReceipt(transaction, receipt -> receipt.operationType = "OTHER");
        assertConflictWithMutatedReceipt(transaction, receipt -> receipt.payloadHash = "different");
    }

    @Test
    void equalNewBalanceWithoutReceiptIsNotApplied() {
        TestState state = new TestState(150, new ArrayList<>());
        CreateClanTransaction transaction = transaction(UUID.fromString("22222222-2222-2222-2222-222222222222"), 250, 150);

        assertEquals(RepositoryResult.Status.CONFLICT,
                PlayerTransactionReceiptLedger.applyDebit(state, transaction, CLOCK, () -> true).status());
        assertTrue(state.receipts().isEmpty());
    }

    @Test
    void receiptWithWrongBalanceIsConflict() {
        TestState state = new TestState(200, new ArrayList<>());
        CreateClanTransaction transaction = transaction(UUID.fromString("22222222-2222-2222-2222-222222222222"), 250, 150);
        state.receipts().add(PlayerTransactionReceiptLedger.receiptFor(transaction, CLOCK.millis()));

        assertEquals(RepositoryResult.Status.CONFLICT,
                PlayerTransactionReceiptLedger.verifyDebit(state, transaction).status());
    }

    @Test
    void failedDurableSaveRestoresBalanceAndReceipt() {
        TestState state = new TestState(250, new ArrayList<>());
        CreateClanTransaction transaction = transaction(UUID.fromString("22222222-2222-2222-2222-222222222222"), 250, 150);

        RepositoryResult result = PlayerTransactionReceiptLedger.applyDebit(state, transaction, CLOCK, () -> false);

        assertEquals(RepositoryResult.Status.FAILED, result.status());
        assertEquals(250, state.coins());
        assertTrue(state.receipts().isEmpty());
    }

    @Test
    void differentTransactionsMayShareNewBalance() {
        CreateClanTransaction first = transaction(UUID.fromString("22222222-2222-2222-2222-222222222222"), 250, 150);
        CreateClanTransaction second = transaction(UUID.fromString("33333333-3333-3333-3333-333333333333"), 250, 150);
        TestState state = new TestState(150, new ArrayList<>());
        state.receipts().add(PlayerTransactionReceiptLedger.receiptFor(first, CLOCK.millis()));

        assertEquals(RepositoryResult.Status.CONFLICT,
                PlayerTransactionReceiptLedger.applyDebit(state, second, CLOCK, () -> true).status());
    }

    @Test
    void duplicateOrInvalidReceiptsAreRejectedDeterministically() {
        CreateClanTransaction transaction = transaction(UUID.fromString("22222222-2222-2222-2222-222222222222"), 250, 150);
        AppliedTransactionReceipt first = PlayerTransactionReceiptLedger.receiptFor(transaction, CLOCK.millis());
        AppliedTransactionReceipt second = PlayerTransactionReceiptLedger.receiptFor(transaction, CLOCK.millis());
        AppliedTransactionReceipt invalid = new AppliedTransactionReceipt();
        invalid.transactionId = UUID.fromString("44444444-4444-4444-4444-444444444444").toString();
        invalid.operationType = "";
        invalid.expectedOldBalance = -1;
        invalid.newBalance = -2;

        List<AppliedTransactionReceipt> normalized = PlayerTransactionReceiptLedger.normalizeReceipts(List.of(first, second, invalid));

        assertTrue(normalized.isEmpty());
    }

    @Test
    void oversizedOperationOrHashIsNotValidReceipt() {
        CreateClanTransaction transaction = transaction(UUID.fromString("22222222-2222-2222-2222-222222222222"), 250, 150);
        AppliedTransactionReceipt receipt = PlayerTransactionReceiptLedger.receiptFor(transaction, CLOCK.millis());
        receipt.operationType = "x".repeat(65);
        assertTrue(PlayerTransactionReceiptLedger.normalizeReceipts(List.of(receipt)).isEmpty());

        receipt.operationType = transaction.operationType();
        receipt.payloadHash = "x".repeat(129);
        assertTrue(PlayerTransactionReceiptLedger.normalizeReceipts(List.of(receipt)).isEmpty());
    }

    @Test
    void setRewardCreditIsAppliedOnceAcrossRepeatedFinalizationAndReload() {
        String key = "set:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa:place:1:player:bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        TestState state = new TestState(10, new ArrayList<>());

        RepositoryResult first = PlayerTransactionReceiptLedger.applyCredit(
                state, key, "set_reward", 35, CLOCK, () -> true);
        String json = GSON.toJson(state);
        TestState reloaded = GSON.fromJson(json, TestState.class);
        reloaded.receipts(PlayerTransactionReceiptLedger.normalizeReceipts(reloaded.receipts()));
        RepositoryResult repeated = PlayerTransactionReceiptLedger.applyCredit(
                reloaded, key, "set_reward", 35, CLOCK, () -> true);

        assertEquals(RepositoryResult.Status.APPLIED, first.status());
        assertEquals(RepositoryResult.Status.ALREADY_APPLIED, repeated.status());
        assertEquals(45, reloaded.coins());
        assertEquals(1, reloaded.receipts().size());
    }

    @Test
    void crashBeforeCreditCommitLeavesOperationAvailableForRetry() {
        String key = "set:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa:place:2:player:bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        TestState state = new TestState(10, new ArrayList<>());

        RepositoryResult failed = PlayerTransactionReceiptLedger.applyCredit(
                state, key, "set_reward", 35, CLOCK, () -> false);
        RepositoryResult retry = PlayerTransactionReceiptLedger.applyCredit(
                state, key, "set_reward", 35, CLOCK, () -> true);

        assertEquals(RepositoryResult.Status.FAILED, failed.status());
        assertEquals(RepositoryResult.Status.APPLIED, retry.status());
        assertEquals(45, state.coins());
        assertEquals(1, state.receipts().size());
    }

    @Test
    void sameSetRewardKeyWithDifferentAmountConflicts() {
        String key = "set:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa:place:3:player:bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        TestState state = new TestState(10, new ArrayList<>());
        PlayerTransactionReceiptLedger.applyCredit(state, key, "set_reward", 35, CLOCK, () -> true);

        RepositoryResult mismatch = PlayerTransactionReceiptLedger.applyCredit(
                state, key, "set_reward", 45, CLOCK, () -> true);

        assertEquals(RepositoryResult.Status.CONFLICT, mismatch.status());
        assertEquals(45, state.coins());
    }

    private static void assertConflictWithMutatedReceipt(
            CreateClanTransaction transaction,
            java.util.function.Consumer<AppliedTransactionReceipt> mutator
    ) {
        TestState state = new TestState(transaction.newBalance(), new ArrayList<>());
        AppliedTransactionReceipt receipt = PlayerTransactionReceiptLedger.receiptFor(transaction, CLOCK.millis());
        mutator.accept(receipt);
        state.receipts().add(receipt);

        assertEquals(RepositoryResult.Status.CONFLICT,
                PlayerTransactionReceiptLedger.applyDebit(state, transaction, CLOCK, () -> true).status());
    }

    private static CreateClanTransaction transaction(UUID transactionId, int oldBalance, int newBalance) {
        UUID player = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String clanId = "55555555-5555-5555-5555-555555555555";
        ClanCreationPayload payload = new ClanCreationPayload(clanId, "Example Clan", "TAG", 0x11AA11, player, "Player");
        return new CreateClanTransaction(1, transactionId, CreateClanTransaction.OPERATION_TYPE, player, "Player", clanId,
                oldBalance, newBalance, payload, CreateClanTransaction.payloadHash(payload), TransactionState.PREPARED,
                CLOCK.millis(), CLOCK.millis(), "");
    }

    private static final class TestState implements PlayerTransactionReceiptLedger.State {
        private int coins;
        private List<AppliedTransactionReceipt> receipts;

        private TestState(int coins, List<AppliedTransactionReceipt> receipts) {
            this.coins = coins;
            this.receipts = receipts;
        }

        @Override
        public int coins() {
            return coins;
        }

        @Override
        public void coins(int value) {
            coins = value;
        }

        @Override
        public List<AppliedTransactionReceipt> receipts() {
            return receipts;
        }

        @Override
        public void receipts(List<AppliedTransactionReceipt> value) {
            receipts = value;
        }
    }
}
