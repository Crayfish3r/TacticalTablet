package com.makar.tacticaltablet.tablet.client;

import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MapVoteClientState {

    private static boolean active;
    private static boolean operator;
    private static boolean nextSetCompetitive;
    private static boolean nextSetClanWar;
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
            int remainingSeconds,
            String selected,
            List<String> mapPool,
            Map<String, Integer> counts
    ) {
        active = voteActive;
        operator = isOperator;
        nextSetCompetitive = competitive;
        nextSetClanWar = clanWar;
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
