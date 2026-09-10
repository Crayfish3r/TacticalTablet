package com.makar.tacticaltablet.client.casino;

import com.makar.tacticaltablet.casino.CasinoRewardKind;
import com.makar.tacticaltablet.casino.net.CasinoSpinResultPacket;

import net.minecraft.client.Minecraft;

import java.util.UUID;

public final class CasinoClientAccess {
    private CasinoClientAccess() {
    }

    public static void open(UUID sessionId, int balance, boolean spectatorSource) {
        // Legacy packet 37 may refresh a matching menu, but cannot create a client-only casino.
        if (Minecraft.getInstance().screen instanceof CasinoScreen screen) screen.refreshBalance(sessionId, balance);
    }

    public static void animation(com.makar.tacticaltablet.casino.net.CasinoAnimationPacket packet) {
        if (Minecraft.getInstance().screen instanceof CasinoScreen screen) screen.acceptAnimation(packet);
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
