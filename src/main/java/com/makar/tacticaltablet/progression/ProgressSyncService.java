package com.makar.tacticaltablet.progression;

import com.makar.tacticaltablet.game.lobby.LobbyManager;

import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** Integration boundary for the existing progression-related client synchronization paths. */
public final class ProgressSyncService {
    public void syncProgress(ServerPlayer player) {
        ClassXPManager.sync(Objects.requireNonNull(player, "player"));
    }

    public void syncTablet(ServerPlayer player) {
        LobbyManager.sync(Objects.requireNonNull(player, "player"));
    }
}
