package com.makar.tacticaltablet.game.respawn;

import com.makar.tacticaltablet.game.MapSetManager;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.tablet.PlayerTabletState;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class RespawnControlManager {

    private static final int XP_PER_UNUSED_LIFE = 10;
    private static final int COINS_PER_UNUSED_LIFE = 10;

    private static boolean respawnsDisabled;

    public static boolean areRespawnsDisabled() {
        return respawnsDisabled;
    }

    public static void reset(MinecraftServer server) {
        respawnsDisabled = false;
        syncAll(server);
    }

    public static void disableRespawns(MinecraftServer server) {
        if (server == null) return;

        if (!respawnsDisabled) {
            respawnsDisabled = true;
            broadcast(server, "[WAR] Возрождения отключены. Началась финальная фаза.");
        }

        eliminateWaitingPlayers(server);
        syncAll(server);
    }

    public static void enableRespawns(MinecraftServer server) {
        respawnsDisabled = false;
        syncAll(server);
    }

    public static void compensateUnusedLives(ServerPlayer player, int unusedLives) {
        if (player == null || unusedLives <= 0) return;

        UnusedLifeReward reward = calculateUnusedLifeReward(unusedLives, LivesManager.MAX_LIVES);
        if (reward.compensatedLives() <= 0) return;

        int xpReward = reward.xp();
        int coinReward = reward.coins();

        PlayerProgressManager.addCoins(player, coinReward);

        String clazz = PlayerTabletState.getSelectedClass(player);
        boolean xpEnabled = !MapSetManager.isCompetitiveSet() && ClassXPManager.isStandardClass(clazz);
        int awardedXp = 0;
        if (xpEnabled) {
            awardedXp = ClassXPManager.addXP(player, clazz, xpReward);
        }

        player.sendSystemMessage(Component.literal("[WAR] Компенсация за неиспользованные жизни: +"
                + coinReward + " монет"
                + (xpEnabled ? ", +" + awardedXp + " опыта" : "")
                + "."));
        PlayerProgressManager.savePlayer(player);
        ClassXPManager.sync(player);
    }

    static UnusedLifeReward calculateUnusedLifeReward(int unusedLives, int maximumLives) {
        int cappedLives = Math.max(0, Math.min(unusedLives, maximumLives - 1));
        return new UnusedLifeReward(
                cappedLives,
                cappedLives * COINS_PER_UNUSED_LIFE,
                cappedLives * XP_PER_UNUSED_LIFE);
    }

    record UnusedLifeReward(int compensatedLives, int coins, int xp) {
    }

    private static void eliminateWaitingPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!LivesManager.hasStarted(player)) continue;
            if (LivesManager.isEliminated(player)) continue;
            if (player.getTags().contains("war.playing")) continue;
            if (LivesManager.getLives(player) <= 0) continue;

            LivesManager.eliminateForRespawnDisabled(player);
        }
    }

    private static void syncAll(MinecraftServer server) {
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ClassXPManager.sync(player);
        }
    }

    private static void broadcast(MinecraftServer server, String message) {
        Component component = Component.literal(message);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(component);
        }
    }
}

