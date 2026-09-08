package com.makar.tacticaltablet.casino;

import com.makar.tacticaltablet.game.MatchPhase;

/** Pure decision policy for the delayed return from casino isolation. */
public final class CasinoReturnPolicy {
    private CasinoReturnPolicy() {
    }

    public static Decision decide(
            boolean deadlineReached,
            boolean matchRunning,
            MatchPhase phase,
            boolean moderator,
            boolean eliminated,
            boolean clanSpectating,
            boolean clanWarSet
    ) {
        if (!deadlineReached) return Decision.WAIT;
        if (!matchRunning || phase != MatchPhase.RUNNING) return Decision.RELEASE_FOR_NEXT_MATCH;
        if (moderator || eliminated || clanSpectating || clanWarSet) return Decision.KEEP_SPECTATING;
        return Decision.ADMIT_ACTIVE_MATCH;
    }

    public enum Decision {
        WAIT,
        RELEASE_FOR_NEXT_MATCH,
        ADMIT_ACTIVE_MATCH,
        KEEP_SPECTATING
    }
}