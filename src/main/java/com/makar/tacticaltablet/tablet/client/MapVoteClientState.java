package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.game.SetGameMode;
import com.makar.tacticaltablet.game.SetModeRotationPolicy;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MapVoteClientState {

    private static boolean active;
    private static boolean operator;
    private static boolean nextSetCompetitive;
    private static boolean nextSetClanWar;
    private static boolean ordinaryModesEnabled;
    private static int availableModesMask = 1 << SetGameMode.CASUAL.ordinal();
    private static int chaosCooldownRemaining;
    private static int consecutiveCompetitiveSets;
    private static SetGameMode selectedMode;
    private static Map<SetGameMode, Integer> modeVoteCounts = Map.of();
    private static int secondsLeft;
    private static String selectedMap = "";
    private static List<String> maps = List.of();
    private static Map<String, Integer> voteCounts = Map.of();

    private MapVoteClientState() {
    }

    public static void update(
            boolean voteActive,
            boolean openScreen,
            boolean isOperator,
            boolean competitive,
            boolean clanWar,
            boolean modesEnabled,
            int modesMask,
            int chaosCooldown,
            int competitiveStreak,
            SetGameMode mode,
            Map<SetGameMode, Integer> modeCounts,
            int remainingSeconds,
            String selected,
            List<String> mapPool,
            Map<String, Integer> counts
    ) {
        active = voteActive;
        operator = isOperator;
        nextSetCompetitive = competitive;
        nextSetClanWar = clanWar;
        ordinaryModesEnabled = modesEnabled;
        availableModesMask = modesMask | (1 << SetGameMode.CASUAL.ordinal());
        chaosCooldownRemaining = Math.max(0, chaosCooldown);
        consecutiveCompetitiveSets = Math.max(0, competitiveStreak);
        selectedMode = mode;
        modeVoteCounts = modeCounts == null ? Map.of() : Map.copyOf(modeCounts);
        secondsLeft = Math.max(0, remainingSeconds);
        selectedMap = selected == null ? "" : selected;
        maps = mapPool == null ? List.of() : List.copyOf(mapPool);
        voteCounts = counts == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(counts));

        Minecraft minecraft = Minecraft.getInstance();
        if (active && openScreen && minecraft.level != null && minecraft.player != null
                && !(minecraft.screen instanceof MapVotingScreen)) {
            minecraft.setScreen(new MapVotingScreen());
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static int getSecondsLeft() {
        return secondsLeft;
    }

    public static boolean isOperator() {
        return operator;
    }

    public static boolean isNextSetCompetitive() {
        return nextSetCompetitive;
    }

    public static boolean isNextSetClanWar() {
        return nextSetClanWar;
    }

    public static boolean areOrdinaryModesEnabled() { return ordinaryModesEnabled; }
    public static boolean isModeAvailable(SetGameMode mode) {
        return SetModeRotationPolicy.isAvailable(availableModesMask, mode);
    }
    public static int getChaosCooldownRemaining() { return chaosCooldownRemaining; }
    public static int getConsecutiveCompetitiveSets() { return consecutiveCompetitiveSets; }
    public static SetGameMode getSelectedMode() { return selectedMode; }
    public static int getModeVoteCount(SetGameMode mode) { return modeVoteCounts.getOrDefault(mode, 0); }

    public static String getSelectedMap() {
        return selectedMap;
    }

    public static List<String> getMaps() {
        return maps;
    }

    public static int getVoteCount(String map) {
        return voteCounts.getOrDefault(map, 0);
    }
}
