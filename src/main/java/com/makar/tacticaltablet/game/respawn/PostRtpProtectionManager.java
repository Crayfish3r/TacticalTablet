package com.makar.tacticaltablet.game.respawn;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Authoritative, server-tick-based damage protection granted after a successful RTP. */
public final class PostRtpProtectionManager {

    private static final Map<UUID, Long> protectedUntilTick = new HashMap<>();

    private PostRtpProtectionManager() {
    }

    public static void grant(ServerPlayer player, int durationTicks) {
        if (player == null || durationTicks <= 0) return;
        grantAtTick(player.getUUID(), currentTick(player), durationTicks);
    }

    public static boolean isProtected(ServerPlayer player) {
        return player != null && isProtectedAtTick(player.getUUID(), currentTick(player));
    }

    public static int remainingTicks(ServerPlayer player) {
        return player == null ? 0 : remainingTicksAtTick(player.getUUID(), currentTick(player));
    }

    public static void clear(UUID playerId) {
        if (playerId != null) protectedUntilTick.remove(playerId);
    }

    public static void clear(ServerPlayer player) {
        if (player != null) clear(player.getUUID());
    }

    public static void clearAll() {
        protectedUntilTick.clear();
    }

    static void grantAtTick(UUID playerId, long currentTick, int durationTicks) {
        if (playerId == null || durationTicks <= 0) return;

        long expiry = currentTick > Long.MAX_VALUE - durationTicks
                ? Long.MAX_VALUE
                : currentTick + durationTicks;
        protectedUntilTick.merge(playerId, expiry, Math::max);
    }

    static boolean isProtectedAtTick(UUID playerId, long currentTick) {
        return remainingTicksAtTick(playerId, currentTick) > 0;
    }

    static int remainingTicksAtTick(UUID playerId, long currentTick) {
        if (playerId == null) return 0;

        Long expiry = protectedUntilTick.get(playerId);
        if (expiry == null) return 0;
        if (currentTick >= expiry) {
            protectedUntilTick.remove(playerId);
            return 0;
        }

        return (int) Math.min(Integer.MAX_VALUE, expiry - currentTick);
    }

    private static long currentTick(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }
}
