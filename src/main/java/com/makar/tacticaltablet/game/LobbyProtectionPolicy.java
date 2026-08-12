package com.makar.tacticaltablet.game;

/** Pure policy for the physical lobby and its short dimension-transition window. */
final class LobbyProtectionPolicy {
    private LobbyProtectionPolicy() {
    }

    static boolean isProtected(boolean physicalLobby, boolean hasLobbyTag, boolean warPlaying) {
        return physicalLobby || (hasLobbyTag && !warPlaying);
    }
}
