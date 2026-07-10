package com.makar.tacticaltablet.game.lifecycle;

import com.makar.tacticaltablet.game.MatchPhase;

public final class LegacyMatchStateMapper {
    private LegacyMatchStateMapper() {
    }

    public static MatchState fromLegacyState(int runningStateValue, int gameState, MatchPhase phase) {
        if (phase == MatchPhase.POST_GAME) {
            return MatchState.ENDING;
        }
        if (phase == MatchPhase.STARTING) {
            return MatchState.STARTING;
        }
        if (gameState == runningStateValue && phase == MatchPhase.RUNNING) {
            return MatchState.RUNNING;
        }
        return MatchState.IDLE;
    }
}
