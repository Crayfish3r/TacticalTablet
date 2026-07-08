package com.makar.tacticaltablet.game;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

public final class DropControlManager {

    private static final boolean BLOCK_DROPS_ENABLED = false;

    private DropControlManager() {
    }

    public static void enforceGameRules(MinecraftServer server) {
        if (server == null) return;

        for (ServerLevel level : server.getAllLevels()) {
            level.getGameRules()
                    .getRule(GameRules.RULE_DOBLOCKDROPS)
                    .set(BLOCK_DROPS_ENABLED, server);
        }
    }
}
