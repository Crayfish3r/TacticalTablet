package com.makar.tacticaltablet.game.lifecycle.integration;

public final class MatchStartRecoveryPolicy {
    private MatchStartRecoveryPolicy() {
    }

    public static boolean shouldRecover(MatchStartStatus status) {
        return status == MatchStartStatus.REJECTED
                || status == MatchStartStatus.FAILED_ROLLED_BACK
                || status == MatchStartStatus.FAILED_REQUIRES_CLEANUP
                || status == MatchStartStatus.BLOCKED_REQUIRES_CLEANUP;
    }
}

