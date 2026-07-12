package com.makar.tacticaltablet.game;

final class TabletLobbyPolicy {
    private TabletLobbyPolicy() {
    }

    static boolean isTabletAvailable(
            boolean running,
            boolean startTransitionPlayerSetup,
            boolean clanWarSet,
            MatchPhase phase
    ) {
        return running
                || startTransitionPlayerSetup
                || (clanWarSet && phase == MatchPhase.WAITING)
                || phase == MatchPhase.VOTING
                || phase == MatchPhase.TEAM_SELECT
                || phase == MatchPhase.MAP_VOTING;
    }
}
