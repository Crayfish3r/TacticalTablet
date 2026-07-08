package com.makar.tacticaltablet.game.clanwar;

import com.makar.tacticaltablet.clan.ClanManager;
import com.makar.tacticaltablet.game.SpectatorCameraManager;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.tablet.PlayerTabletState;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ClanWarManager {

    public static final int PRE_START_WAIT_SECONDS = 60;
    public static final int CLAN_LIVES_PER_GAME = 2;
    public static final String TAG_SPECTATING = "war.clan_spectating";
    public static final String TAG_REGROUP_PENDING = "war.clan_regroup_pending";

    private static final Map<String, Integer> clanLives = new HashMap<>();
    private static final Set<String> eliminatedClans = new HashSet<>();
    private static int preStartWaitLeft = -1;
    private static boolean soloDebug;

    private ClanWarManager() {
    }

    public static void resetRuntime() {
        clanLives.clear();
        eliminatedClans.clear();
        preStartWaitLeft = -1;
    }

    public static boolean tickPreStartWait(MinecraftServer server) {
        if (server == null) return false;
        if (preStartWaitLeft < 0) {
            preStartWaitLeft = PRE_START_WAIT_SECONDS;
            broadcast(server, "[WAR] Война кланов: ожидание игроков 60 секунд.");
        }
        if (preStartWaitLeft > 0) {
            if (preStartWaitLeft == PRE_START_WAIT_SECONDS || preStartWaitLeft <= 5 || preStartWaitLeft % 15 == 0) {
                broadcast(server, "[WAR] Война кланов начнет подготовку через " + preStartWaitLeft + " сек.");
            }
            preStartWaitLeft--;
            return true;
        }
        return false;
    }

    public static void skipPreStartWait() {
        preStartWaitLeft = 0;
    }

    public static void startMatch(MinecraftServer server) {
        resetRuntime();
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.removeTag(TAG_SPECTATING);
            player.removeTag(TAG_REGROUP_PENDING);
            String clanId = ClanManager.getClanIdForPlayer(player);
            if (clanId.isBlank()) {
                continue;
            }
            clanLives.putIfAbsent(clanId, CLAN_LIVES_PER_GAME);
        }
    }

    public static boolean isSoloDebugEnabled() {
        return soloDebug;
    }

    public static void setSoloDebugEnabled(boolean enabled) {
        soloDebug = enabled;
    }

    public static boolean hasClan(ServerPlayer player) {
        return player != null && !ClanManager.getClanIdForPlayer(player).isBlank();
    }

    public static int getClanLives(String clanId) {
        return clanLives.getOrDefault(clanId, 0);
    }

    public static boolean isClanEliminated(String clanId) {
        return clanId == null || clanId.isBlank() || eliminatedClans.contains(clanId) || getClanLives(clanId) <= 0;
    }

    public static void showNeedClan(ServerPlayer player) {
        if (player == null) return;
        player.sendSystemMessage(Component.literal("[WAR] Нужно вступить в клан."));
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Нужно вступить в клан")));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Война кланов доступна только участникам кланов")));
    }

    public static boolean shouldKeepSpectating(ServerPlayer player) {
        return player != null && player.getTags().contains(TAG_SPECTATING);
    }

    public static boolean shouldMoveToLobbyAfterDeath(ServerPlayer player) {
        return player != null && player.getTags().contains(TAG_REGROUP_PENDING);
    }

    public static boolean isClanWiped(MinecraftServer server, String clanId) {
        if (server == null || clanId == null || clanId.isBlank()) return false;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!clanId.equals(ClanManager.getClanIdForPlayer(player))) continue;
            if (player.getTags().contains("war.playing") && player.isAlive() && !player.isDeadOrDying()) {
                return false;
            }
        }
        return true;
    }

    public static int consumeClanLife(MinecraftServer server, String clanId) {
        if (server == null || clanId == null || clanId.isBlank()) return 0;
        int remaining = Math.max(0, clanLives.getOrDefault(clanId, CLAN_LIVES_PER_GAME) - 1);
        clanLives.put(clanId, remaining);
        broadcast(server, "[WAR] Клан " + ClanManager.getClanNameById(server, clanId)
                + " потерял жизнь. Осталось: " + remaining + ".");
        if (remaining <= 0) {
            eliminatedClans.add(clanId);
        }
        return remaining;
    }

    public static void eliminateClan(MinecraftServer server, String clanId) {
        if (server == null || clanId == null || clanId.isBlank()) return;
        clanLives.put(clanId, 0);
        if (eliminatedClans.add(clanId)) {
            broadcast(server, "[WAR] РљР»Р°РЅ " + ClanManager.getClanNameById(server, clanId) + " РІС‹Р±С‹Р».");
        }
    }

    public static int getAliveClanCount(MinecraftServer server) {
        if (server == null) return 0;
        Set<String> alive = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String clanId = ClanManager.getClanIdForPlayer(player);
            if (clanId.isBlank() || isClanEliminated(clanId)) continue;
            alive.add(clanId);
        }
        return alive.size();
    }

    public static int getAliveUnitCount(MinecraftServer server) {
        if (server == null) return 0;
        Set<String> aliveClans = new HashSet<>();
        int aliveSoloPlayers = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String clanId = ClanManager.getClanIdForPlayer(player);
            if (!clanId.isBlank()) {
                if (!isClanEliminated(clanId)) {
                    aliveClans.add(clanId);
                }
            } else if (LivesManager.isAliveParticipant(player)) {
                aliveSoloPlayers++;
            }
        }
        return aliveClans.size() + aliveSoloPlayers;
    }

    public static ServerPlayer findWinningUnitRepresentative(MinecraftServer server) {
        if (server == null) return null;
        Set<String> seenClans = new HashSet<>();
        int aliveUnits = 0;
        ServerPlayer winner = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String clanId = ClanManager.getClanIdForPlayer(player);
            if (!clanId.isBlank()) {
                if (isClanEliminated(clanId) || !seenClans.add(clanId)) continue;
            } else if (!LivesManager.isAliveParticipant(player)) {
                continue;
            }

            aliveUnits++;
            winner = player;
            if (aliveUnits > 1) return null;
        }
        return winner;
    }

    public static ServerPlayer findWinningClanRepresentative(MinecraftServer server) {
        if (server == null) return null;
        String winnerClan = "";
        ServerPlayer winner = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String clanId = ClanManager.getClanIdForPlayer(player);
            if (clanId.isBlank() || isClanEliminated(clanId)) continue;
            if (winnerClan.isBlank()) {
                winnerClan = clanId;
                winner = player;
            } else if (!winnerClan.equals(clanId)) {
                return null;
            }
        }
        return winner;
    }

    public static void markClanForRegroup(MinecraftServer server, String clanId) {
        if (server == null || clanId == null || clanId.isBlank()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!clanId.equals(ClanManager.getClanIdForPlayer(player))) continue;
            player.removeTag(TAG_SPECTATING);
            player.addTag(TAG_REGROUP_PENDING);
        }
    }

    public static void preparePlayerForRegroup(ServerPlayer player) {
        if (player == null) return;
        player.removeTag(TAG_SPECTATING);
        player.removeTag(TAG_REGROUP_PENDING);
        player.removeTag("war.playing");
        PlayerTabletState.reset(player);
    }

    public static void keepSpectator(ServerPlayer player) {
        if (player == null) return;
        player.setGameMode(GameType.SPECTATOR);
        player.removeTag("war.playing");
        player.addTag(TAG_SPECTATING);
        SpectatorCameraManager.onPlayerEliminated(player);
    }

    private static void broadcast(MinecraftServer server, String message) {
        Component component = Component.literal(message);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(component);
        }
    }
}
