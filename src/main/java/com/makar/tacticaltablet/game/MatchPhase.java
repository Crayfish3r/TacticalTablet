package com.makar.tacticaltablet.game;

public enum MatchPhase {
    WAITING,
    VOTING,
    TEAM_SELECT,
    STARTING,
    RUNNING,
    POST_GAME,
    MAP_VOTING,
    RESTARTING;

    public static MatchPhase byId(int id) {
        MatchPhase[] values = values();
        if (id < 0 || id >= values.length) {
            throw new IllegalArgumentException("Invalid match phase id: " + id);
        }
        return values[id];
    }
}
