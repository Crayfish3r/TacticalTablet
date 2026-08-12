package com.makar.tacticaltablet.client;

import com.makar.tacticaltablet.game.SpectatorHudSnapshot;

import java.util.Optional;

/** Client-only snapshot state; contains no server or Minecraft runtime objects. */
public final class SpectatorHudClientState {
    private static SpectatorHudSnapshot snapshot;

    private SpectatorHudClientState() {
    }

    public static void update(SpectatorHudSnapshot value) {
        snapshot = value;
    }

    public static Optional<SpectatorHudSnapshot> snapshot() {
        return Optional.ofNullable(snapshot);
    }

    public static void clear() {
        snapshot = null;
    }
}
