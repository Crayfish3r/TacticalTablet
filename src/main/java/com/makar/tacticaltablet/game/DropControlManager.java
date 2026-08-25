package com.makar.tacticaltablet.game;

import net.minecraft.server.MinecraftServer;

/** Compatibility facade retained for existing integrations. */
public final class DropControlManager {
    private DropControlManager() {
    }

    public static void enforceGameRules(MinecraftServer server) {
        MatchGameRules.apply(server);
    }
}
