package com.makar.tacticaltablet.progression;

record TierUpgradeResult(
        ProgressionStatus status,
        boolean changed,
        int previousTier,
        int currentTier,
        int previousBalance,
        int currentBalance
) {
}
