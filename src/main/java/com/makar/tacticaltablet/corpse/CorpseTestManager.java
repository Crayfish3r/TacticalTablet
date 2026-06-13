package com.makar.tacticaltablet.corpse;

public final class CorpseTestManager {

    private static boolean ownCorpseLootEnabled = false;

    private CorpseTestManager() {
    }

    public static boolean canLootOwnCorpses() {
        return ownCorpseLootEnabled;
    }

    public static void setOwnCorpseLootEnabled(boolean enabled) {
        ownCorpseLootEnabled = enabled;
    }

    public static boolean toggleOwnCorpseLoot() {
        ownCorpseLootEnabled = !ownCorpseLootEnabled;
        return ownCorpseLootEnabled;
    }
}
