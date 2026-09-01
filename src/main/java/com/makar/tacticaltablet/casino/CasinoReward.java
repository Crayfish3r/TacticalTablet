package com.makar.tacticaltablet.casino;

import java.util.Objects;

public record CasinoReward(
        CasinoRewardKind kind,
        int coins,
        String classId,
        int duplicateCompensation
) {
    public CasinoReward {
        Objects.requireNonNull(kind, "kind");
        classId = classId == null ? "" : classId;
        if (coins < 0 || duplicateCompensation < 0) {
            throw new IllegalArgumentException("Casino reward amounts must be non-negative");
        }
    }

    public static CasinoReward coins(int amount) {
        return new CasinoReward(CasinoRewardKind.COINS, amount, "", 0);
    }

    public static CasinoReward shopClass(String classId, int duplicateCompensation) {
        return new CasinoReward(CasinoRewardKind.SHOP_CLASS, 0, classId, duplicateCompensation);
    }

    public static CasinoReward vipClass(String classId, int duplicateCompensation) {
        return new CasinoReward(CasinoRewardKind.VIP_CLASS, 0, classId, duplicateCompensation);
    }

    public static CasinoReward sadTrombone(int duplicateCompensation) {
        return new CasinoReward(CasinoRewardKind.SAD_TROMBONE, 0, "", duplicateCompensation);
    }
}
