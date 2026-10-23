package com.makar.tacticaltablet.game.lifecycle;

import net.minecraft.server.level.ServerPlayer;

/** One-time cleanup metadata only; these tags no longer participate in admission or match rules. */
public final class RetiredClanWarTags {
    private static final String SPECTATING = "war.clan_spectating";
    private static final String REGROUP = "war.clan_regroup_pending";
    private static final String PENDING_LOBBY = "tacticaltablet.retired_clan_war_lobby_cleanup";
    private RetiredClanWarTags() { }

    public static void clear(ServerPlayer player) {
        if (player == null) return;
        boolean hadSpectating = player.removeTag(SPECTATING);
        boolean hadRegroup = player.removeTag(REGROUP);
        if (hadSpectating || hadRegroup) player.getPersistentData().putBoolean(PENDING_LOBBY, true);
    }

    public static boolean pendingLobbyCleanup(ServerPlayer player) {
        return player != null && player.getPersistentData().getBoolean(PENDING_LOBBY);
    }

    public static void finish(ServerPlayer player) { player.getPersistentData().remove(PENDING_LOBBY); }
}
