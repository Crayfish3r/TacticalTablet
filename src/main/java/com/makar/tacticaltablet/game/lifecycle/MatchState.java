package com.makar.tacticaltablet.game.lifecycle;

public enum MatchState {
    IDLE,
    PREPARING,
    STARTING,
    RUNNING,
    ENDING,
    CLEANING,
    FAILED;

    public boolean hasActiveContext() {
        return this != IDLE;
    }

    public boolean isPlayerVisibleActiveMatch() {
        return this == RUNNING || this == ENDING;
    }

    public boolean canAcceptStartRequest() {
        return this == IDLE;
    }

    public boolean canAcceptStopRequest() {
        return this == RUNNING || this == ENDING || this == FAILED;
    }

    public boolean needsCleanup() {
        return this == PREPARING
                || this == STARTING
                || this == RUNNING
                || this == ENDING
                || this == CLEANING
                || this == FAILED;
    }
}
