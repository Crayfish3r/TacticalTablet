package com.makar.tacticaltablet.progression;

final class AppliedTransactionReceipt {
    String transactionId = "";
    String operationType = "";
    long appliedAt;
    int expectedOldBalance;
    int newBalance;
    String payloadHash = "";
}
