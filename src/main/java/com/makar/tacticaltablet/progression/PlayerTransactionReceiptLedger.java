package com.makar.tacticaltablet.progression;

import com.makar.tacticaltablet.clan.transaction.CreateClanTransaction;
import com.makar.tacticaltablet.clan.transaction.RepositoryResult;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

final class PlayerTransactionReceiptLedger {
    private static final int MAX_OPERATION_LENGTH = 64;
    private static final int MAX_HASH_LENGTH = 128;

    private PlayerTransactionReceiptLedger() {
    }

    interface State {
        int coins();

        void coins(int value);

        List<AppliedTransactionReceipt> receipts();

        void receipts(List<AppliedTransactionReceipt> value);
    }

    static List<AppliedTransactionReceipt> normalizeReceipts(List<AppliedTransactionReceipt> receipts) {
        if (receipts == null || receipts.isEmpty()) return new ArrayList<>();

        Map<String, Integer> counts = new HashMap<>();
        for (AppliedTransactionReceipt receipt : receipts) {
            if (receipt != null && receipt.transactionId != null) {
                counts.merge(receipt.transactionId, 1, Integer::sum);
            }
        }

        List<AppliedTransactionReceipt> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AppliedTransactionReceipt receipt : receipts) {
            if (!isValidReceipt(receipt)) continue;
            if (counts.getOrDefault(receipt.transactionId, 0) != 1) continue;
            if (seen.add(receipt.transactionId)) {
                normalized.add(receipt);
            }
        }
        return normalized;
    }

    static RepositoryResult applyDebit(State state, CreateClanTransaction transaction, Clock clock, BooleanSupplier durableSave) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(durableSave, "durableSave");

        AppliedTransactionReceipt receipt = findReceipt(state.receipts(), transaction.transactionId().toString());
        if (receipt != null) {
            return receiptMatches(receipt, transaction) && state.coins() == transaction.newBalance()
                    ? RepositoryResult.alreadyApplied()
                    : RepositoryResult.conflict("Player debit receipt conflicts with transaction");
        }
        if (state.coins() == transaction.newBalance()) {
            return RepositoryResult.conflict("Player balance matches debit result without transaction receipt");
        }
        if (state.coins() != transaction.expectedOldBalance()) {
            return RepositoryResult.conflict("Player balance does not match transaction precondition");
        }

        int previousCoins = state.coins();
        List<AppliedTransactionReceipt> previousReceipts = new ArrayList<>(state.receipts());
        state.coins(transaction.newBalance());
        state.receipts().add(receiptFor(transaction, clock.millis()));
        if (durableSave.getAsBoolean()) {
            return RepositoryResult.applied();
        }
        state.coins(previousCoins);
        state.receipts(previousReceipts);
        return RepositoryResult.failed("Failed to persist player progress debit receipt", null);
    }

    static RepositoryResult verifyDebit(State state, CreateClanTransaction transaction) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(transaction, "transaction");

        AppliedTransactionReceipt receipt = findReceipt(state.receipts(), transaction.transactionId().toString());
        if (receipt == null) {
            return RepositoryResult.conflict("Player debit receipt is missing");
        }
        if (!receiptMatches(receipt, transaction)) {
            return RepositoryResult.conflict("Player debit receipt does not match transaction");
        }
        return state.coins() == transaction.newBalance()
                ? RepositoryResult.alreadyApplied()
                : RepositoryResult.conflict("Player balance does not match debit receipt");
    }

    static AppliedTransactionReceipt receiptFor(CreateClanTransaction transaction, long appliedAt) {
        AppliedTransactionReceipt receipt = new AppliedTransactionReceipt();
        receipt.transactionId = transaction.transactionId().toString();
        receipt.operationType = transaction.operationType();
        receipt.appliedAt = appliedAt;
        receipt.expectedOldBalance = transaction.expectedOldBalance();
        receipt.newBalance = transaction.newBalance();
        receipt.payloadHash = transaction.payloadHash();
        return receipt;
    }

    static boolean receiptMatches(AppliedTransactionReceipt receipt, CreateClanTransaction transaction) {
        return receipt != null
                && Objects.equals(receipt.transactionId, transaction.transactionId().toString())
                && Objects.equals(receipt.operationType, transaction.operationType())
                && receipt.expectedOldBalance == transaction.expectedOldBalance()
                && receipt.newBalance == transaction.newBalance()
                && Objects.equals(receipt.payloadHash, transaction.payloadHash());
    }

    private static AppliedTransactionReceipt findReceipt(List<AppliedTransactionReceipt> receipts, String transactionId) {
        if (receipts == null || transactionId == null) return null;
        for (AppliedTransactionReceipt receipt : receipts) {
            if (receipt != null && transactionId.equals(receipt.transactionId) && isValidReceipt(receipt)) {
                return receipt;
            }
        }
        return null;
    }

    private static boolean isValidReceipt(AppliedTransactionReceipt receipt) {
        return receipt != null
                && receipt.transactionId != null && !receipt.transactionId.isBlank()
                && receipt.operationType != null && !receipt.operationType.isBlank()
                && receipt.operationType.length() <= MAX_OPERATION_LENGTH
                && receipt.expectedOldBalance >= 0
                && receipt.newBalance >= 0
                && receipt.expectedOldBalance > receipt.newBalance
                && receipt.payloadHash != null && !receipt.payloadHash.isBlank()
                && receipt.payloadHash.length() <= MAX_HASH_LENGTH;
    }
}
