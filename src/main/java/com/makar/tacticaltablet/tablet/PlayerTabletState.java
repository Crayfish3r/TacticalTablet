package com.makar.tacticaltablet.tablet;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerTabletState {

    private static final class State {
        private boolean kitUsed;
        private boolean rtpUsed;
        private String selectedClass = "";
    }

    private static final Map<UUID, State> states = new HashMap<>();

    public static void setSelectedClass(ServerPlayer player, String clazz) {
        if (player == null) return;
        getState(player).selectedClass = clazz == null ? "" : clazz;
    }

    public static String getSelectedClass(ServerPlayer player) {
        if (player == null) return "";

        State state = states.get(player.getUUID());
        return state == null ? "" : state.selectedClass;
    }

    public static boolean hasSelectedClass(ServerPlayer player) {
        return !getSelectedClass(player).isBlank();
    }

    public static boolean isKitUsed(ServerPlayer player) {
        if (player == null) return false;

        State state = states.get(player.getUUID());
        return state != null && state.kitUsed;
    }

    public static boolean isRtpUsed(ServerPlayer player) {
        if (player == null) return false;

        State state = states.get(player.getUUID());
        return state != null && state.rtpUsed;
    }

    public static void setKitUsed(ServerPlayer player) {
        if (player == null) return;
        getState(player).kitUsed = true;
    }

    public static void setRtpUsed(ServerPlayer player) {
        if (player == null) return;
        getState(player).rtpUsed = true;
    }

    public static void reset(ServerPlayer player) {
        if (player == null) return;
        states.remove(player.getUUID());
    }

    public static void resetAll() {
        states.clear();
    }

    private static State getState(ServerPlayer player) {
        return states.computeIfAbsent(player.getUUID(), uuid -> new State());
    }
}

