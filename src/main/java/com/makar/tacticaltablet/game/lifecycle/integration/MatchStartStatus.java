package com.makar.tacticaltablet.game.lifecycle.integration;

public enum MatchStartStatus {
    STARTED,
    REJECTED,
    FAILED_ROLLED_BACK,
    FAILED_REQUIRES_CLEANUP,
    ALREADY_STARTING,
    ALREADY_RUNNING,
    STALE_OPERATION
}
