package com.makar.tacticaltablet.integration.moderndamage.client;

import com.makar.tacticaltablet.integration.moderndamage.ModernDamageIntegration;
import net.minecraftforge.common.MinecraftForge;

/** Invoked through DistExecutor only after the exact MDC version gate succeeds. */
public final class ModernDamageClientBootstrap {
    private static boolean initialized;

    private ModernDamageClientBootstrap() {
    }

    public static synchronized void initialize() {
        if (initialized || !ModernDamageIntegration.isSupported()) return;
        ModernDamageClientAccessV1032 access = new ModernDamageClientAccessV1032();
        access.disableStockStaminaHudIfRequired();
        MinecraftForge.EVENT_BUS.register(new ModernDamageStaminaOverlay(access));
        initialized = true;
    }
}
