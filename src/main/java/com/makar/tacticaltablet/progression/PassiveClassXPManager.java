package com.makar.tacticaltablet.progression;

import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.tablet.PlayerTabletState;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PassiveClassXPManager {

    private static final int TICKS_PER_SECOND = 20;
    private static final int PASSIVE_INTERVAL_SECONDS = 120;
    private static final int PASSIVE_XP = 2;

    private static final Map<UUID, Integer> playedSeconds = new HashMap<>();
    private static int tickCounter;

    public static void tick(MinecraftServer server) {
        if (server == null) return;

        if (++tickCounter < TICKS_PER_SECOND) {
            return;
        }

        tickCounter = 0;

        Set<UUID> activePlayers = new HashSet<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            activePlayers.add(uuid);

            if (!isEligible(player)) {
                playedSeconds.remove(uuid);
                continue;
            }

            int seconds = playedSeconds.getOrDefault(uuid, 0) + 1;
            if (seconds >= PASSIVE_INTERVAL_SECONDS) {
                seconds = 0;

                String clazz = PlayerTabletState.getSelectedClass(player);
                int awardedXp = ClassXPManager.addXP(player, clazz, PASSIVE_XP);
                XpNotifier.send(player, awardedXp, "class survival time");
            }

            playedSeconds.put(uuid, seconds);
        }

        playedSeconds.keySet().removeIf(uuid -> !activePlayers.contains(uuid));
    }

    public static void clear(ServerPlayer player) {
        if (player == null) return;
        playedSeconds.remove(player.getUUID());
    }

    public static void clearAll() {
        playedSeconds.clear();
        tickCounter = 0;
    }

    private static boolean isEligible(ServerPlayer player) {
        if (player == null) return false;
        if (!GameStateManager.isRunning(player.server)) return false;
        if (!player.getTags().contains("war.playing")) return false;
        if (!LivesManager.isAliveParticipant(player)) return false;

        String clazz = PlayerTabletState.getSelectedClass(player);
        return ClassXPManager.isStandardClass(clazz);
    }
}

