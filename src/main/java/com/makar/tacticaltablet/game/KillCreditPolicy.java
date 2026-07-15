package com.makar.tacticaltablet.game;

/** Pure classification for the single kill-stat/coin award path. */
public final class KillCreditPolicy {
    private KillCreditPolicy() {
    }

    public static Outcome classify(
            boolean killerPresent,
            boolean killerParticipating,
            boolean selfKill,
            boolean victimOwnedProjectile,
            boolean teammates
    ) {
        if (!killerPresent || !killerParticipating || selfKill || victimOwnedProjectile) return Outcome.IGNORE;
        return teammates ? Outcome.TEAM_KILL : Outcome.REWARD;
    }

    public enum Outcome {
        IGNORE,
        TEAM_KILL,
        REWARD
    }
}
