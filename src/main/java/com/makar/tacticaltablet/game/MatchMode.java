package com.makar.tacticaltablet.game;

import java.util.ArrayList;
import java.util.List;

public enum MatchMode {
    SOLO("Соло", 1, 3, 2),
    DUO("Дуо", 2, 1, 4),
    TRIO("Трио", 3, 1, 5),
    SQUADS("Отряды", 5, 1, 13);

    public static final int MAX_DUO_PLAYERS = 8;

    private final String displayName;
    private final int teamSize;
    private final int livesPerPlayer;
    private final int minPlayers;

    MatchMode(String displayName, int teamSize, int livesPerPlayer, int minPlayers) {
        this.displayName = displayName;
        this.teamSize = teamSize;
        this.livesPerPlayer = livesPerPlayer;
        this.minPlayers = minPlayers;
    }

    public String displayName() {
        return displayName;
    }

    public int teamSize() {
        return teamSize;
    }

    public int livesPerPlayer() {
        return livesPerPlayer;
    }

    public int minPlayers() {
        return minPlayers;
    }

    public boolean isTeamMode() {
        return teamSize > 1;
    }

    public boolean isSelectableFor(int onlinePlayers, boolean includeDebugModes) {
        if (this == DUO && onlinePlayers > MAX_DUO_PLAYERS) return false;
        if (includeDebugModes) return true;
        if (this == SOLO) return true;

        if (onlinePlayers >= SQUADS.minPlayers()) {
            return this == SQUADS;
        }

        if (onlinePlayers >= TRIO.minPlayers()) {
            return this == TRIO || (this == DUO && onlinePlayers <= MAX_DUO_PLAYERS);
        }

        if (onlinePlayers >= DUO.minPlayers()) {
            return this == DUO;
        }

        return false;
    }

    public static List<MatchMode> selectableModes(int onlinePlayers, boolean includeDebugModes) {
        List<MatchMode> result = new ArrayList<>();
        for (MatchMode mode : values()) {
            if (mode.isSelectableFor(onlinePlayers, includeDebugModes)) {
                result.add(mode);
            }
        }
        return List.copyOf(result);
    }

    public static int voteMaskFor(int onlinePlayers, boolean includeDebugModes) {
        int mask = 0;
        for (MatchMode mode : selectableModes(onlinePlayers, includeDebugModes)) {
            mask |= 1 << mode.ordinal();
        }
        return mask;
    }

    public static MatchMode sanitizeForOnlineCount(int onlinePlayers, MatchMode selected, boolean includeDebugModes) {
        if (selected == null) return SOLO;
        if (selected.isSelectableFor(onlinePlayers, includeDebugModes)) return selected;
        if (includeDebugModes && !(selected == DUO && onlinePlayers > MAX_DUO_PLAYERS)) return selected;

        if (selected.isTeamMode()) {
            if (onlinePlayers >= SQUADS.minPlayers()) return SQUADS;
            if (onlinePlayers >= TRIO.minPlayers()) return TRIO;
            if (onlinePlayers >= DUO.minPlayers()) return DUO;
        }

        return SOLO;
    }

    public static MatchMode byId(int id) {
        MatchMode[] values = values();
        if (id < 0 || id >= values.length) {
            throw new IllegalArgumentException("Invalid match mode id: " + id);
        }
        return values[id];
    }
}
