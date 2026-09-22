package com.makar.tacticaltablet.game;

import java.util.EnumSet;
import java.util.Set;

public final class SetModeRotationPolicy {
    public static final int CHAOS_NON_CHAOS_COOLDOWN = 3;
    public static final int MAX_CONSECUTIVE_COMPETITIVE = 2;
    public static final int COMPETITIVE_MIN_PLAYERS = 5;

    private SetModeRotationPolicy() { }

    public record RotationState(int chaosCooldownRemaining, int consecutiveCompetitiveSets) {
        public RotationState {
            chaosCooldownRemaining = Math.max(0,
                    Math.min(CHAOS_NON_CHAOS_COOLDOWN, chaosCooldownRemaining));
            consecutiveCompetitiveSets = Math.max(0, consecutiveCompetitiveSets);
        }
    }

    public static RotationState recordCompletedSet(RotationState state, SetGameMode completedMode) {
        RotationState current = state == null ? new RotationState(0, 0) : state;
        SetGameMode actualMode = completedMode == null ? SetGameMode.CASUAL : completedMode;

        int chaosCooldown = actualMode == SetGameMode.CHAOS
                ? CHAOS_NON_CHAOS_COOLDOWN
                : Math.max(0, current.chaosCooldownRemaining() - 1);
        int competitiveStreak = actualMode == SetGameMode.COMPETITIVE
                ? (int) Math.min(Integer.MAX_VALUE,
                        (long) current.consecutiveCompetitiveSets() + 1L)
                : 0;
        return new RotationState(chaosCooldown, competitiveStreak);
    }

    public static Set<SetGameMode> availableModes(RotationState state, int eligiblePlayers) {
        RotationState current = state == null ? new RotationState(0, 0) : state;
        EnumSet<SetGameMode> available = EnumSet.of(SetGameMode.CASUAL);
        if (current.chaosCooldownRemaining() == 0) available.add(SetGameMode.CHAOS);
        if (eligiblePlayers >= COMPETITIVE_MIN_PLAYERS
                && current.consecutiveCompetitiveSets() < MAX_CONSECUTIVE_COMPETITIVE) {
            available.add(SetGameMode.COMPETITIVE);
        }
        return Set.copyOf(available);
    }

    public static boolean isAvailable(Set<SetGameMode> availableModes, SetGameMode mode) {
        return mode != null && availableModes != null && availableModes.contains(mode);
    }

    public static int availabilityMask(Set<SetGameMode> availableModes) {
        int mask = 1 << SetGameMode.CASUAL.ordinal();
        if (availableModes == null) return mask;
        for (SetGameMode mode : availableModes) {
            if (mode != null && mode.selectable()) mask |= 1 << mode.ordinal();
        }
        return mask;
    }

    public static boolean isAvailable(int availabilityMask, SetGameMode mode) {
        return mode != null && mode.selectable() && (availabilityMask & (1 << mode.ordinal())) != 0;
    }

    public static SetGameMode resolveCompetitivePreStart(SetGameMode requestedMode, int eligiblePlayers) {
        if (requestedMode == SetGameMode.COMPETITIVE
                && eligiblePlayers < COMPETITIVE_MIN_PLAYERS) {
            return SetGameMode.CASUAL;
        }
        return requestedMode == null ? SetGameMode.CASUAL : requestedMode;
    }

    public static boolean shouldRecordCompletion(int previousCompletedGames,
                                                 int completedGames,
                                                 int gamesPerSet) {
        return gamesPerSet > 0
                && previousCompletedGames < gamesPerSet
                && completedGames >= gamesPerSet;
    }
}
