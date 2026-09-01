package com.makar.tacticaltablet.progression;

import com.makar.tacticaltablet.casino.CasinoRewardKind;

public record CasinoProgressResult(
        Status status,
        int balance,
        CasinoRewardKind rewardKind,
        int awardedCoins,
        String classId,
        boolean duplicate,
        String diagnostic
) {
    public enum Status {
        APPLIED,
        ALREADY_APPLIED,
        INSUFFICIENT_COINS,
        INVALID_REQUEST,
        SAVE_FAILED
    }

    public boolean successful() {
        return status == Status.APPLIED || status == Status.ALREADY_APPLIED;
    }
}
