package com.makar.tacticaltablet.game.lifecycle.integration;

public enum MatchStartRejectionReason {
    NONE,
    SERVER_UNAVAILABLE,
    LIFECYCLE_NOT_IDLE,
    RUNTIME_REQUIREMENTS_FAILED,
    INSUFFICIENT_PLAYERS,
    INVALID_MODE,
    UNKNOWN
}
