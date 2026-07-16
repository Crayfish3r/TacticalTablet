package com.makar.tacticaltablet.progression;

final class AppliedTransactionReceipt {
    String transactionId = "";
    String operationType = "";
    long appliedAt;
    int expectedOldBalance;
    int newBalance;
    String payloadHash = "";

    ProgressReceipt toProgressReceipt() {
        return new ProgressReceipt(
                transactionId,
                operationType,
                appliedAt,
                expectedOldBalance,
                newBalance,
                payloadHash
        );
    }

    static AppliedTransactionReceipt from(ProgressReceipt receipt) {
        AppliedTransactionReceipt applied = new AppliedTransactionReceipt();
        applied.transactionId = receipt.transactionId();
        applied.operationType = receipt.operationType();
        applied.appliedAt = receipt.appliedAt();
        applied.expectedOldBalance = receipt.expectedOldBalance();
        applied.newBalance = receipt.newBalance();
        applied.payloadHash = receipt.payloadHash();
        return applied;
    }
}
