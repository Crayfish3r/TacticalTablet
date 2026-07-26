package com.makar.tacticaltablet.game;

public enum MatchAdmissionOutcome {
    ACTIVE_PARTICIPANT,
    RETURNING_PARTICIPANT,
    LATE_SPECTATOR,
    NORMAL_LOBBY_PLAYER,
    DISCONNECTED;

    MatchAdmissionStatus legacyStatus() {
        return switch (this) {
            case ACTIVE_PARTICIPANT, RETURNING_PARTICIPANT -> MatchAdmissionStatus.ADMITTED;
            case LATE_SPECTATOR -> MatchAdmissionStatus.LATE_SPECTATOR;
            case NORMAL_LOBBY_PLAYER, DISCONNECTED -> MatchAdmissionStatus.NO_ACTIVE_MATCH;
        };
    }
}
