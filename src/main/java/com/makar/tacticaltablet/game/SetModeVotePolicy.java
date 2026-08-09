package com.makar.tacticaltablet.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class SetModeVotePolicy {
    private SetModeVotePolicy() { }

    public static SetGameMode selectWinner(Map<SetGameMode, Integer> counts, RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        int best = 0;
        for (SetGameMode mode : SetGameMode.values()) {
            if (mode.selectable()) best = Math.max(best, Math.max(0, counts == null ? 0 : counts.getOrDefault(mode, 0)));
        }
        if (best == 0) return SetGameMode.CASUAL;
        List<SetGameMode> leaders = new ArrayList<>();
        for (SetGameMode mode : SetGameMode.values()) {
            if (mode.selectable() && Math.max(0, counts == null ? 0 : counts.getOrDefault(mode, 0)) == best) {
                leaders.add(mode);
            }
        }
        return leaders.get(random.nextInt(leaders.size()));
    }

    public static boolean ordinaryModesEnabled(boolean competitive, boolean clanWar) {
        return !competitive && !clanWar;
    }
}
