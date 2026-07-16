package com.makar.tacticaltablet.progression;

import java.util.Optional;

/**
 * Package-private mutation boundary over the single profile object owned by PlayerProgressManager.
 * Thread safety is supplied by the synchronized facade; implementations do not add their own locks.
 */
interface MutableProgressState extends ExclusiveUnlockState {
    int coins();

    void coins(int value);

    ProgressEntry tier(String classId);

    void tier(String classId, int value);

    ProgressEntry baseUnlock(String classId);

    void baseUnlock(String classId, int value);

    void removeBaseUnlock(String classId);

    int counter(Counter counter);

    void counter(Counter counter, int value);

    boolean flag(Flag flag);

    void flag(Flag flag, boolean value);

    Optional<ProgressReceipt> receipt(String receiptId);

    void addReceipt(ProgressReceipt receipt);

    boolean removeReceipt(ProgressReceipt receipt);

    enum Counter {
        WINS,
        KILLS,
        DEATHS,
        MATCHES_PLAYED,
        BATTLE_PASS_XP
    }

    enum Flag {
        XP_BOOST,
        SAD_TROMBONE_KILLS
    }
}

interface ExclusiveUnlockState {
    ProgressEntry experience(String classId);

    void experience(String classId, int value);

    void removeExperience(String classId);

    ProgressEntry purchase(String classId);

    void purchase(String classId, int value);

    void removePurchase(String classId);
}

record ProgressEntry(boolean present, int value) {
    static ProgressEntry from(Integer value) {
        return value == null ? new ProgressEntry(false, 0) : new ProgressEntry(true, value);
    }
}

record ProgressReceipt(
        String transactionId,
        String operationType,
        long appliedAt,
        int expectedOldBalance,
        int newBalance,
        String payloadHash
) {
}
