package com.makar.tacticaltablet.tablet.client;

import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Set;
import java.util.Map;

public final class ChaosClientState {
    private static boolean active;
    private static int gameNumber;
    private static List<String> offered = List.of();
    private static Map<String, Integer> tiers = Map.of();
    private static Set<String> spent = Set.of();
    private static String selected = "";
    private static boolean requiresSelection;

    private ChaosClientState() { }

    public static void update(boolean enabled, int game, List<String> classes, Map<String, Integer> offeredTiers, Set<String> used,
                              String current, boolean required) {
        active = enabled;
        gameNumber = Math.max(0, game);
        offered = classes == null ? List.of() : List.copyOf(classes);
        tiers = offeredTiers == null ? Map.of() : Map.copyOf(offeredTiers);
        spent = used == null ? Set.of() : Set.copyOf(used);
        selected = current == null ? "" : current;
        requiresSelection = enabled && required;
        Minecraft minecraft = Minecraft.getInstance();
        if (requiresSelection && minecraft.player != null && minecraft.level != null
                && !(minecraft.screen instanceof TabletScreen)) minecraft.setScreen(new TabletScreen());
    }

    public static boolean isActive() { return active; }
    public static int gameNumber() { return gameNumber; }
    public static List<String> offered() { return offered; }
    public static boolean isOffered(String id) { return offered.contains(id); }
    public static boolean isSpent(String id) { return spent.contains(id); }
    public static int tier(String id) { return tiers.getOrDefault(id, 0); }
    public static String selected() { return selected; }
    public static boolean requiresSelection() { return requiresSelection; }
    public static int availableCount() { return Math.max(0, offered.size() - spent.size() - (selected.isBlank() ? 0 : 1)); }
    public static void clear() { update(false, 0, List.of(), Map.of(), Set.of(), "", false); }
}
