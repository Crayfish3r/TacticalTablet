package com.makar.tacticaltablet.integration.moderndamage.client;

import com.makar.tacticaltablet.integration.moderndamage.net.MdcBalanceStatePacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ModernDamageClientState {
    private static volatile MdcBalanceStatePacket snapshot;
    private static volatile long updateCounter;

    private ModernDamageClientState() {
    }

    public static void accept(MdcBalanceStatePacket packet) {
        snapshot = packet;
        updateCounter++;
    }

    public static MdcBalanceStatePacket snapshot() {
        return snapshot;
    }

    public static long updateCounter() {
        return updateCounter;
    }

    public static void clear() {
        snapshot = null;
        updateCounter++;
    }
}
