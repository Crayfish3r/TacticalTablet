package com.makar.tacticaltablet.game.lives;

import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.respawn.RespawnControlManager;
import com.makar.tacticaltablet.game.respawn.RtpTimerManager;
import com.makar.tacticaltablet.inventory.InventoryManager;
import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.progression.PassiveClassXPManager;
import com.makar.tacticaltablet.tablet.PlayerTabletState;

import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

public class LivesManager {

    public static final String OBJECTIVE = "lives";
    public static final int MAX_LIVES = 3;

    private static final String TAG_LIVES_INIT = "war.lives_init";
    private static final String TAG_ELIMINATED = "war.eliminated";

    public static void ensureStarted(ServerPlayer player) {
        if (player == null) return;
        if (player.getTags().contains(TAG_LIVES_INIT)) return;
        if (player.getTags().contains(TAG_ELIMINATED)) return;

        setLives(player, GameStateManager.getLivesPerPlayer());
        player.addTag(TAG_LIVES_INIT);
    }

    public static int getLives(ServerPlayer player) {
        if (player == null) return 0;

        Scoreboard scoreboard = player.getScoreboard();
        Objective objective = getOrCreateLivesObjective(player);

        if (objective == null) {
            return GameStateManager.getLivesPerPlayer();
        }

        return scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective).getScore();
    }

    public static void setLives(ServerPlayer player, int lives) {
        if (player == null) return;

        Scoreboard scoreboard = player.getScoreboard();
        Objective objective = getOrCreateLivesObjective(player);

        if (objective == null) {
            return;
        }

        scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective).setScore(lives);
    }

    public static boolean isEliminated(ServerPlayer player) {
        return player != null && player.getTags().contains(TAG_ELIMINATED);
    }

    public static boolean hasStarted(ServerPlayer player) {
        return player != null && player.getTags().contains(TAG_LIVES_INIT);
    }

    public static boolean canContinueMatch(ServerPlayer player) {
        if (player == null) return false;
        if (isEliminated(player)) return false;

        if (!player.getTags().contains(TAG_LIVES_INIT)) {
            return true;
        }

        return getLives(player) > 0;
    }

    public static boolean ensureEliminatedIfOutOfLives(ServerPlayer player) {
        if (player == null) return false;

        if (isEliminated(player)) {
            moveEliminatedToSpectator(player);
            return true;
        }

        if (hasStarted(player) && getLives(player) <= 0) {
            eliminate(player, 0);
            return true;
        }

        return false;
    }

    public static boolean isAliveParticipant(ServerPlayer player) {
        if (player == null) return false;
        if (!player.getTags().contains(TAG_LIVES_INIT)) return false;
        if (isEliminated(player)) return false;

        return getLives(player) > 0;
    }

    public static int getAlivePlayerCount(MinecraftServer server) {
        if (server == null) return 0;

        int count = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isAliveParticipant(player)) {
                count++;
            }
        }

        return count;
    }

    public static int getRemainingLivesTotal(MinecraftServer server) {
        if (server == null) return 0;

        int total = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isAliveParticipant(player)) {
                total += Math.max(0, getLives(player));
            }
        }

        return total;
    }

    public static int handleDeath(ServerPlayer victim) {
        if (victim == null) return 0;

        if (!victim.getTags().contains(TAG_LIVES_INIT)) {
            ensureStarted(victim);
        }

        int lives = Math.max(0, getLives(victim) - 1);
        setLives(victim, lives);

        victim.removeTag("war.playing");
        RtpTimerManager.cancel(victim);
        PassiveClassXPManager.clear(victim);

        if (lives <= 0 || RespawnControlManager.areRespawnsDisabled()) {
            eliminate(victim, lives);
        } else {
            victim.addTag("in_lobby");
            victim.sendSystemMessage(Component.literal("[WAR] Осталось жизней: " + lives));
        }

        return lives;
    }

    public static void eliminateForRespawnDisabled(ServerPlayer player) {
        if (player == null) return;

        int unusedLives = Math.max(0, getLives(player));
        player.removeTag("war.playing");
        player.removeTag("in_lobby");
        RtpTimerManager.cancel(player);
        PassiveClassXPManager.clear(player);
        eliminate(player, unusedLives);
    }

    private static void eliminate(ServerPlayer player, int unusedLives) {
        player.removeTag("in_lobby");
        player.addTag(TAG_ELIMINATED);

        if (RespawnControlManager.areRespawnsDisabled() && unusedLives > 0) {
            RespawnControlManager.compensateUnusedLives(player, unusedLives);
        }

        setLives(player, 0);
        player.sendSystemMessage(Component.literal("[WAR] Ты выбыл из матча. Включён режим наблюдателя."));

        if (!player.isDeadOrDying()) {
            moveEliminatedToSpectator(player);
        }
    }

    public static void moveEliminatedToSpectator(ServerPlayer player) {
        if (player == null) return;

        MinecraftServer server = player.server;
        ServerLevel overworld = GameStateManager.getOverworld(server);
        if (overworld == null) return;

        RtpTimerManager.cancel(player);
        PassiveClassXPManager.clear(player);
        PlayerTabletState.reset(player);
        InventoryManager.clearInventory(player);

        player.removeTag("war.playing");
        player.removeTag("in_lobby");

        BlockPos spawn = overworld.getSharedSpawnPos();
        double x = spawn.getX() + 0.5D;
        double y = spawn.getY() + 2.0D;
        double z = spawn.getZ() + 0.5D;

        if (player.level().dimension().equals(overworld.dimension())) {
            x = player.getX();
            y = player.getY();
            z = player.getZ();
        }

        player.teleportTo(overworld, x, y, z, player.getYRot(), player.getXRot());
        player.setGameMode(GameType.SPECTATOR);
        ClassXPManager.sync(player);
    }

    public static void resetPlayer(ServerPlayer player) {
        if (player == null) return;

        player.removeTag(TAG_LIVES_INIT);
        player.removeTag(TAG_ELIMINATED);
        setLives(player, GameStateManager.getLivesPerPlayer());
    }

    public static void resetAll(MinecraftServer server) {
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            resetPlayer(player);
        }
    }

    private static Objective getOrCreateLivesObjective(ServerPlayer player) {
        Scoreboard scoreboard = player.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE);
        if (objective != null) return objective;

        CommandSourceStack source = player.server.createCommandSourceStack()
                .withSuppressedOutput()
                .withPermission(4);
        player.server.getCommands().performPrefixedCommand(
                source,
                "scoreboard objectives add " + OBJECTIVE + " dummy"
        );

        return scoreboard.getObjective(OBJECTIVE);
    }
}

