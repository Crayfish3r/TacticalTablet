package com.makar.tacticaltablet.anticheat;

import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.lives.LivesManager;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class MovementAntiCheat {

    private static final long GRACE_MS = 5_000L;
    private static final long MIN_SAMPLE_MS = 250L;
    private static final double MAX_BLOCKS_PER_SECOND = 18.0D;
    private static final double EXTRA_DISTANCE_ALLOWANCE = 6.0D;

    private static final Map<UUID, MovementState> states = new HashMap<>();

    private MovementAntiCheat() {
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;

        long now = System.currentTimeMillis();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            check(player, now);
        }

        states.keySet().removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
    }

    public static void reset(ServerPlayer player) {
        if (player == null) return;
        states.remove(player.getUUID());
    }

    public static void resetAll() {
        states.clear();
    }

    private static void check(ServerPlayer player, long now) {
        UUID uuid = player.getUUID();

        if (!shouldCheck(player)) {
            states.remove(uuid);
            return;
        }

        ResourceKey<Level> dimension = player.level().dimension();
        MovementState state = states.get(uuid);

        if (state == null || !state.dimension.equals(dimension)) {
            states.put(uuid, MovementState.create(player, now, dimension, now + GRACE_MS));
            return;
        }

        long elapsedMs = now - state.lastTime;
        if (elapsedMs < MIN_SAMPLE_MS) {
            return;
        }

        double dx = player.getX() - state.x;
        double dy = player.getY() - state.y;
        double dz = player.getZ() - state.z;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        double elapsedSeconds = elapsedMs / 1000.0D;
        double allowed = MAX_BLOCKS_PER_SECOND * elapsedSeconds + EXTRA_DISTANCE_ALLOWANCE;

        if (now > state.graceUntil && distanceSq > allowed * allowed) {
            AntiCheatManager.record(
                    player,
                    ViolationType.MOVEMENT_ANOMALY,
                    Severity.HIGH,
                    "distance=" + format(Math.sqrt(distanceSq))
                            + " allowed=" + format(allowed)
                            + " elapsedMs=" + elapsedMs
            );
        }

        state.update(player, now, dimension);
    }

    private static boolean shouldCheck(ServerPlayer player) {
        if (player == null) return false;
        if (player.isSpectator()) return false;
        if (LivesManager.isEliminated(player)) return false;
        if (GameStateManager.isInLobby(player) || player.getTags().contains("in_lobby")) return false;

        return player.getTags().contains("war.playing");
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static final class MovementState {
        private double x;
        private double y;
        private double z;
        private long lastTime;
        private long graceUntil;
        private ResourceKey<Level> dimension;

        private static MovementState create(
                ServerPlayer player,
                long now,
                ResourceKey<Level> dimension,
                long graceUntil
        ) {
            MovementState state = new MovementState();
            state.graceUntil = graceUntil;
            state.update(player, now, dimension);
            return state;
        }

        private void update(ServerPlayer player, long now, ResourceKey<Level> newDimension) {
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
            this.lastTime = now;
            this.dimension = newDimension;
        }
    }
}
