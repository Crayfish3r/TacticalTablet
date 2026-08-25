package com.makar.tacticaltablet.game.lobby;

import net.minecraft.world.level.GameType;

/** Keeps intentional spectators while normalizing ordinary lobby players. */
public final class LobbyGameModePolicy {
    private LobbyGameModePolicy() {
    }

    public static GameType target(
            GameType current,
            boolean moderator,
            boolean forcedSpectator,
            boolean ordinaryRespawn
    ) {
        if (moderator || forcedSpectator) {
            return GameType.SPECTATOR;
        }
        if (!ordinaryRespawn && current == GameType.SPECTATOR) return GameType.SPECTATOR;
        return GameType.SURVIVAL;
    }
}
