package com.makar.tacticaltablet.progression;

record BaseUnlockResult(
        ProgressionStatus status,
        boolean changed,
        int previousBalance,
        int currentBalance
) {
}
