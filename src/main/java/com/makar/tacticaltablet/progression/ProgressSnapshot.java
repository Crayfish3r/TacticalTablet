package com.makar.tacticaltablet.progression;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable, Minecraft-free payload handed to the persistence thread. */
record ProgressSnapshot(String key, long revision, Data data) {
    ProgressSnapshot {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(data, "data");
    }

    /** JSON-compatible immutable copy of a player progress document. */
    record Data(
            int dataVersion, String name, String uuid,
            Map<String, Integer> classes, Map<String, Integer> classTiers,
            Map<String, Integer> unlockedBaseClasses,
            int wins, int kills, int deaths, int matchesPlayed, int coins, int battlePassXp,
            boolean xpBoost, boolean sadTromboneKills,
            Map<String, Integer> purchasedClasses, Set<String> purchasedCosmetics,
            Map<String, Integer> donations, Map<String, Integer> stats,
            List<ProgressReceipt> appliedTransactionReceipts,
            long firstSeen, long lastSeen
    ) {
        Data {
            classes = Map.copyOf(classes);
            classTiers = Map.copyOf(classTiers);
            unlockedBaseClasses = Map.copyOf(unlockedBaseClasses);
            purchasedClasses = Map.copyOf(purchasedClasses);
            purchasedCosmetics = Set.copyOf(purchasedCosmetics);
            donations = Map.copyOf(donations);
            stats = Map.copyOf(stats);
            appliedTransactionReceipts = List.copyOf(appliedTransactionReceipts);
        }

        Data(
                int dataVersion, String name, String uuid,
                Map<String, Integer> classes, Map<String, Integer> classTiers,
                Map<String, Integer> unlockedBaseClasses,
                int wins, int kills, int deaths, int matchesPlayed, int coins, int battlePassXp,
                boolean xpBoost, boolean sadTromboneKills,
                Map<String, Integer> purchasedClasses, Map<String, Integer> donations, Map<String, Integer> stats,
                List<ProgressReceipt> appliedTransactionReceipts, long firstSeen, long lastSeen
        ) {
            this(dataVersion, name, uuid, classes, classTiers, unlockedBaseClasses,
                    wins, kills, deaths, matchesPlayed, coins, battlePassXp, xpBoost, sadTromboneKills,
                    purchasedClasses, Set.of(), donations, stats, appliedTransactionReceipts, firstSeen, lastSeen);
        }
    }
}
