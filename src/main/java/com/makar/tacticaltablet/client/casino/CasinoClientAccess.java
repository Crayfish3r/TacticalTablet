package com.makar.tacticaltablet.client.casino;

import com.makar.tacticaltablet.casino.CasinoRewardKind;
import com.makar.tacticaltablet.casino.net.CasinoSpinResultPacket;

import net.minecraft.client.Minecraft;

import java.util.UUID;

public final class CasinoClientAccess {
    private CasinoClientAccess() {
    }

    public static void open(UUID sessionId, int balance, boolean spectatorSource) {
        Minecraft.getInstance().setScreen(new CasinoScreen(sessionId, balance, spectatorSource));
    }

    public static void result(
            UUID sessionId,
            UUID requestId,
            CasinoSpinResultPacket.Status status,
            int balance,
            CasinoRewardKind rewardKind,
            int awardedCoins,
            String classId,
            boolean duplicate,
            int animationTicks,
            long animationSeed
    ) {
        if (Minecraft.getInstance().screen instanceof CasinoScreen screen) {
            screen.acceptResult(sessionId, requestId, status, balance, rewardKind, awardedCoins,
                    classId, duplicate, animationTicks, animationSeed);
        }
    }
}
