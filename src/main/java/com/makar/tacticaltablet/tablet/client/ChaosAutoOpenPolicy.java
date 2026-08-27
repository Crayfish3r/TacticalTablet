package com.makar.tacticaltablet.tablet.client;

final class ChaosAutoOpenPolicy {
    private ChaosAutoOpenPolicy() { }

    static boolean shouldOpen(boolean requiresSelection, boolean deathOverlayActive,
                              boolean inLobby, boolean survivalMode,
                              boolean hasTablet, boolean tabletAlreadyOpen) {
        return requiresSelection
                && !deathOverlayActive
                && inLobby
                && survivalMode
                && hasTablet
                && !tabletAlreadyOpen;
    }
}
