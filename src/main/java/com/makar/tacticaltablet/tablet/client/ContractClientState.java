package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.tablet.net.ContractSelectionStatePacket;

import java.util.ArrayList;
import java.util.List;

public final class ContractClientState {

    private static boolean selectionActive;
    private static int selectionSecondsLeft;
    private static long cooldownLeftMs;
    private static boolean hasActiveContract;
    private static boolean soloMode = true;
    private static List<ContractSelectionStatePacket.TargetEntry> targets = new ArrayList<>();

    private static boolean trackerActive;
    private static String targetName = "";
    private static String targetClass = "";
    private static int targetKills;
    private static int targetWins;
    private static int targetCareerPercent;
    private static int difficulty;
    private static int price;
    private static int reward;
    private static int zoneCenterX;
    private static int zoneCenterZ;
    private static int zoneRadius = 180;
    private static int playerX;
    private static int playerZ;
    private static int targetAreaX;
    private static int targetAreaZ;
    private static int targetAreaRadius;
    private static int signalSecondsLeft;

    private ContractClientState() {
    }

    public static void updateSelection(
            boolean active,
            int secondsLeft,
            long cooldownMs,
            boolean hasContract,
            boolean isSoloMode,
            List<ContractSelectionStatePacket.TargetEntry> entries
    ) {
        selectionActive = active;
        selectionSecondsLeft = Math.max(0, secondsLeft);
        cooldownLeftMs = Math.max(0L, cooldownMs);
        hasActiveContract = hasContract;
        soloMode = isSoloMode;
        targets = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
    }

    public static void updateTracker(
            boolean active,
            String name,
            String clazz,
            int kills,
            int wins,
            int careerPercent,
            int difficultyId,
            int contractPrice,
            int contractReward,
            int centerX,
            int centerZ,
            int radius,
            int selfX,
            int selfZ,
            int areaX,
            int areaZ,
            int areaRadius,
            int signalLeft
    ) {
        trackerActive = active;
        targetName = name == null ? "" : name;
        targetClass = clazz == null ? "" : clazz;
        targetKills = Math.max(0, kills);
        targetWins = Math.max(0, wins);
        targetCareerPercent = Math.max(0, Math.min(100, careerPercent));
        difficulty = Math.max(0, difficultyId);
        price = Math.max(0, contractPrice);
        reward = Math.max(0, contractReward);
        zoneCenterX = centerX;
        zoneCenterZ = centerZ;
        zoneRadius = Math.max(1, radius);
        playerX = selfX;
        playerZ = selfZ;
        targetAreaX = areaX;
        targetAreaZ = areaZ;
        targetAreaRadius = Math.max(0, areaRadius);
        signalSecondsLeft = Math.max(0, signalLeft);
    }

    public static boolean isSelectionActive() {
        return selectionActive;
    }

    public static int getSelectionSecondsLeft() {
        return selectionSecondsLeft;
    }

    public static long getCooldownLeftMs() {
        return cooldownLeftMs;
    }

    public static boolean hasActiveContract() {
        return hasActiveContract;
    }

    public static boolean isSoloMode() {
        return soloMode;
    }

    public static List<ContractSelectionStatePacket.TargetEntry> getTargets() {
        return List.copyOf(targets);
    }

    public static boolean isTrackerActive() {
        return trackerActive;
    }

    public static String getTargetName() {
        return targetName;
    }

    public static String getTargetClass() {
        return targetClass;
    }

    public static int getTargetKills() {
        return targetKills;
    }

    public static int getTargetWins() {
        return targetWins;
    }

    public static int getTargetCareerPercent() {
        return targetCareerPercent;
    }

    public static int getDifficulty() {
        return difficulty;
    }

    public static int getPrice() {
        return price;
    }

    public static int getReward() {
        return reward;
    }

    public static int getZoneCenterX() {
        return zoneCenterX;
    }

    public static int getZoneCenterZ() {
        return zoneCenterZ;
    }

    public static int getZoneRadius() {
        return zoneRadius;
    }

    public static int getPlayerX() {
        return playerX;
    }

    public static int getPlayerZ() {
        return playerZ;
    }

    public static int getTargetAreaX() {
        return targetAreaX;
    }

    public static int getTargetAreaZ() {
        return targetAreaZ;
    }

    public static int getTargetAreaRadius() {
        return targetAreaRadius;
    }

    public static int getSignalSecondsLeft() {
        return signalSecondsLeft;
    }

    public static int difficultyColor(int difficultyId) {
        if (difficultyId >= 2) return 0xFFFF5555;
        if (difficultyId == 1) return 0xFFFFD966;
        return 0xFF66FF66;
    }
}
