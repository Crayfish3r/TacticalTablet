package com.makar.tacticaltablet.game.lifecycle;

public final class MatchTransitionPolicy {
    private MatchTransitionPolicy() {
    }

    public static boolean canTransition(MatchState source, MatchState destination) {
        if (source == null || destination == null) return false;
        if (source == destination) return true;

        return switch (source) {
            case IDLE -> destination == MatchState.PREPARING;
            case PREPARING -> destination == MatchState.STARTING
                    || destination == MatchState.CLEANING
                    || destination == MatchState.FAILED;
            case STARTING -> destination == MatchState.RUNNING
                    || destination == MatchState.CLEANING
                    || destination == MatchState.FAILED;
            case RUNNING -> destination == MatchState.ENDING
                    || destination == MatchState.CLEANING
                    || destination == MatchState.FAILED;
            case ENDING -> destination == MatchState.CLEANING
                    || destination == MatchState.FAILED;
            case CLEANING -> destination == MatchState.IDLE
                    || destination == MatchState.FAILED;
            case FAILED -> destination == MatchState.CLEANING
                    || destination == MatchState.IDLE;
        };
    }
}
