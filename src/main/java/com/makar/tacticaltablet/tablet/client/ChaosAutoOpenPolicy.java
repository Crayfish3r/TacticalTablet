package com.makar.tacticaltablet.tablet.client;

final class ChaosAutoOpenPolicy {
    private ChaosAutoOpenPolicy() { }

    static boolean shouldOpen(boolean requiresSelection, boolean deathOverlayActive,
                              boolean inLobby, boolean hasTablet, boolean tabletAlreadyOpen) {
        return requiresSelection
                && !deathOverlayActive
                && inLobby
                && hasTablet
                && !tabletAlreadyOpen;
    }
}
