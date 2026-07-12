package com.makar.tacticaltablet.game.zone;

final class ZonePacingPolicy {
    static final int SMALL_MATCH_MAX_PLAYERS = 4;
    static final double SMALL_MATCH_INITIAL_ZONE_SIZE = 180.0D;
    static final int FINAL_REVEAL_INTERVAL_SECONDS = 30;
    static final int FINAL_REVEAL_DURATION_TICKS = 5 * 20;

    private ZonePacingPolicy() {
    }

    static int initialPhaseIndex(int playerCount, int smallMatchInitialPhaseIndex) {
        if (playerCount >= 1 && playerCount <= SMALL_MATCH_MAX_PLAYERS) {
            return smallMatchInitialPhaseIndex;
        }

        return 0;
    }

    static boolean finalRevealEnabled(int phaseIndex, int finalRevealStartPhaseIndex, int phaseCount) {
        return phaseIndex >= finalRevealStartPhaseIndex && phaseIndex < phaseCount;
    }

    static boolean isFinalRevealTarget(boolean aliveParticipant, boolean spectator, boolean playingTag) {
        return aliveParticipant && !spectator && playingTag;
    }
}
