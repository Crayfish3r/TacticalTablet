package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.game.arena.ArenaSnapshot;
import net.minecraft.client.Minecraft;

public final class ArenaClientState {
    private static ArenaSnapshot snapshot = ArenaSnapshot.EMPTY;
    private static long revision;
    private ArenaClientState() { }
    public static void update(ArenaSnapshot value) { snapshot = value; revision++; }
    public static void clear() { update(ArenaSnapshot.EMPTY); }
    public static ArenaSnapshot snapshot() { return snapshot; }
    public static long revision() { return revision; }
    public static int ownScore() {
        var player = Minecraft.getInstance().player;
        return player == null ? 0 : snapshot.rows().stream().filter(r -> r.playerId().equals(player.getUUID()))
                .findFirst().map(ArenaSnapshot.Row::score).orElse(0);
    }
    public static boolean unlimitedLives() {
        var player = Minecraft.getInstance().player;
        return snapshot.active() && player != null && snapshot.rows().stream().anyMatch(r -> r.playerId().equals(player.getUUID()));
    }
}
