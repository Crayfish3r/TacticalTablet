package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.tablet.net.ContractSelectionStatePacket;
import com.makar.tacticaltablet.tablet.net.ContractTrackerStatePacket;

import java.util.ArrayList;
import java.util.List;

public final class ContractClientState {

    private static boolean selectionActive;
    private static int selectionSecondsLeft;
    private static long selectionEndsAtMs;
    private static long cooldownEndsAtMs;
    private static boolean hasActiveContract;
    private static boolean contractsEnabled = true;
    private static List<ContractSelectionStatePacket.TargetEntry> targets = new ArrayList<>();

    private static boolean trackerActive;
    private static int zoneCenterX;
    private static int zoneCenterZ;
    private static int zoneRadius = 180;
    private static int playerX;
    private static int playerZ;
    private static long signalEndsAtMs;
    private static List<ContractTrackerStatePacket.TargetEntry> trackerTargets = new ArrayList<>();

    private ContractClientState() {
    }

    public static void updateSelection(
            boolean active,
            int secondsLeft,
            long cooldownMs,
            boolean hasContract,
            boolean enabled,
            List<ContractSelectionStatePacket.TargetEntry> entries
    ) {
        long now = System.currentTimeMillis();
        selectionActive = active;
        selectionSecondsLeft = Math.max(0, secondsLeft);
        selectionEndsAtMs = now + selectionSecondsLeft * 1000L;
        cooldownEndsAtMs = now + Math.max(0L, cooldownMs);
        hasActiveContract = hasContract;
        contractsEnabled = enabled;
        targets = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
    }

    public static void updateTracker(
            boolean active,
            int centerX,
            int centerZ,
            int radius,
            int selfX,
            int selfZ,
            int signalLeft,
            List<ContractTrackerStatePacket.TargetEntry> entries
    ) {
        long now = System.currentTimeMillis();
        trackerActive = active;
        zoneCenterX = centerX;
        zoneCenterZ = centerZ;
        zoneRadius = Math.max(1, radius);
        playerX = selfX;
        playerZ = selfZ;
        signalEndsAtMs = now + Math.max(0, signalLeft) * 1000L;
        trackerTargets = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
    }

    public static boolean isSelectionActive() {
        return selectionActive;
    }

    public static int getSelectionSecondsLeft() {
        long left = selectionEndsAtMs - System.currentTimeMillis();
        return Math.max(0, (int) ((left + 999L) / 1000L));
    }

    public static long getCooldownLeftMs() {
        return Math.max(0L, cooldownEndsAtMs - System.currentTimeMillis());
    }

    public static boolean hasActiveContract() {
        return hasActiveContract;
    }

    public static boolean isSoloMode() {
        return contractsEnabled;
    }

    public static List<ContractSelectionStatePacket.TargetEntry> getTargets() {
        return List.copyOf(targets);
    }

    public static boolean isTrackerActive() {
        return trackerActive && !trackerTargets.isEmpty();
    }

    public static List<ContractTrackerStatePacket.TargetEntry> getTrackerTargets() {
        return List.copyOf(trackerTargets);
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

    public static int getSignalSecondsLeft() {
        long left = signalEndsAtMs - System.currentTimeMillis();
        return Math.max(0, (int) ((left + 999L) / 1000L));
    }

    public static int difficultyColor(int difficultyId) {
        if (difficultyId >= 2) return 0xFFFF5555;
        if (difficultyId == 1) return 0xFFFFD966;
        return 0xFF66FF66;
    }
}
