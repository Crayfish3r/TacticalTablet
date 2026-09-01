package com.makar.tacticaltablet.progression;

import com.makar.tacticaltablet.casino.CasinoRewardKind;

import java.util.Objects;

public record CasinoProgressRequest(
        String transactionId,
        int stake,
        CasinoRewardKind rewardKind,
        int coinReward,
        String classId,
        int duplicateCompensation
) {
    public CasinoProgressRequest {
        transactionId = transactionId == null ? "" : transactionId;
        Objects.requireNonNull(rewardKind, "rewardKind");
        classId = classId == null ? "" : classId;
    }
}
