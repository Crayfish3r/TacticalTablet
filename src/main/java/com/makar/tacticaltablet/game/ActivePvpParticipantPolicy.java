package com.makar.tacticaltablet.game;

public final class ActivePvpParticipantPolicy {
    private ActivePvpParticipantPolicy() {
    }

    public static boolean isEligible(
            boolean currentMatchParticipant,
            boolean warPlaying,
            boolean lateSpectator,
            boolean spectator,
            boolean moderator,
            boolean eliminated
    ) {
        return currentMatchParticipant
                && warPlaying
                && !lateSpectator
                && !spectator
                && !moderator
                && !eliminated;
    }
}
