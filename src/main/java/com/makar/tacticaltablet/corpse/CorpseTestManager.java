package com.makar.tacticaltablet.corpse;

import net.minecraft.server.level.ServerPlayer;

public final class CorpseTestManager {

    private static volatile boolean ownCorpseLootEnabled = false;

    private CorpseTestManager() {
    }

    public static boolean canLootOwnCorpses() {
        return ownCorpseLootEnabled;
    }

    public static boolean canLootOwnCorpses(ServerPlayer player) {
        return player != null && player.hasPermissions(2) && ownCorpseLootEnabled;
    }

    public static void setOwnCorpseLootEnabled(boolean enabled) {
        ownCorpseLootEnabled = enabled;
    }

    public static boolean toggleOwnCorpseLoot() {
        ownCorpseLootEnabled = !ownCorpseLootEnabled;
        return ownCorpseLootEnabled;
    }
}
