package com.makar.tacticaltablet.casino;

import com.makar.tacticaltablet.game.MatchPhase;

/** Pure admission rules shared by the NPC and spectator entry points. */
public final class CasinoAdmissionPolicy {
    private CasinoAdmissionPolicy() {
    }

    @Deprecated
    public static boolean allowsNpc(boolean inLobby, boolean matchRunning, boolean startTransitionSetup, boolean moderMode, MatchPhase phase) {
        return allowsMachine(inLobby, matchRunning, startTransitionSetup, moderMode, phase);
    }

    public static boolean allowsMachine(
            boolean inLobby,
            boolean matchRunning,
            boolean startTransitionSetup,
            boolean moderMode,
            MatchPhase phase
    ) {
        if (!inLobby || matchRunning || startTransitionSetup || moderMode || phase == null) return false;
        return phase == MatchPhase.WAITING
                || phase == MatchPhase.VOTING
                || phase == MatchPhase.TEAM_SELECT
                || phase == MatchPhase.STARTING
                || phase == MatchPhase.POST_GAME
                || phase == MatchPhase.SET_REWARDING;
    }

    public static boolean allowsSpectator(
            boolean spectator,
            boolean matchRunning,
            boolean moderMode,
            MatchPhase phase
    ) {
        return spectator && matchRunning && !moderMode && phase == MatchPhase.RUNNING;
    }
}
