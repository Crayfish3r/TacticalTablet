package com.makar.tacticaltablet.voice;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.game.team.TeamId;
import com.makar.tacticaltablet.game.team.TeamMatchManager;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.PlayerConnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VoiceChatTeamManager {

    private static final int MAX_VOICE_TEAMS = 8;
    private static final Map<Integer, Group> teamGroups = new ConcurrentHashMap<>();

    private static volatile VoicechatServerApi api;
    private static volatile MinecraftServer matchServer;

    private VoiceChatTeamManager() {
    }

    public static void onVoicechatServerStarted(VoicechatServerStartedEvent event) {
        api = event.getVoicechat();

        MinecraftServer server = matchServer;
        if (server != null) {
            server.execute(() -> startTeamMatch(server));
        }
    }

    public static void onPlayerConnected(PlayerConnectedEvent event) {
        VoicechatConnection connection = event.getConnection();
        ServerPlayer player = resolvePlayer(connection);
        if (player == null) return;

        player.server.execute(() -> assignPlayerToVoiceGroup(player, connection));
    }

    public static synchronized void startTeamMatch(MinecraftServer server) {
        if (server == null
                || !GameStateManager.isRunning(server)
                || GameStateManager.getMatchPhase() != MatchPhase.RUNNING
                || !GameStateManager.getCurrentMode().isTeamMode()) {
            endMatch(server);
            return;
        }

        endMatch(server);
        matchServer = server;
        ensureVoiceGroups();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            assignPlayerToVoiceGroup(player);
        }
    }

    public static void assignPlayerToVoiceGroup(ServerPlayer player) {
        VoicechatServerApi currentApi = api;
        if (player == null || currentApi == null) return;

        VoicechatConnection connection = currentApi.getConnectionOf(player.getUUID());
        if (connection != null) {
            assignPlayerToVoiceGroup(player, connection);
        }
    }

    public static void onPlayerTeamChanged(ServerPlayer player) {
        assignPlayerToVoiceGroup(player);
    }

    public static void removePlayerFromVoiceGroup(ServerPlayer player) {
        VoicechatServerApi currentApi = api;
        if (player == null || currentApi == null) return;

        VoicechatConnection connection = currentApi.getConnectionOf(player.getUUID());
        if (connection != null) {
            connection.setGroup(null);
        }
    }

    public static boolean isPlayerVoiceEligible(ServerPlayer player) {
        return player != null
                && GameStateManager.isRunning(player.server)
                && GameStateManager.getMatchPhase() == MatchPhase.RUNNING
                && GameStateManager.getCurrentMode().isTeamMode()
                && TeamMatchManager.getTeam(player) != null
                && player.getTags().contains("war.playing")
                && !player.getTags().contains("in_lobby")
                && player.isAlive()
                && !player.isDeadOrDying()
                && !player.isSpectator()
                && !LivesManager.isEliminated(player)
                && LivesManager.isAliveParticipant(player);
    }

    public static synchronized void endMatch(MinecraftServer server) {
        matchServer = null;
        VoicechatServerApi currentApi = api;

        if (currentApi != null && server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                VoicechatConnection connection = currentApi.getConnectionOf(player.getUUID());
                if (connection != null) {
                    connection.setGroup(null);
                }
            }
        }

        if (currentApi != null) {
            for (Group group : teamGroups.values()) {
                currentApi.removeGroup(group.getId());
            }
        }
        teamGroups.clear();
    }

    public static synchronized void shutdown(MinecraftServer server) {
        endMatch(server);
        api = null;
    }

    private static void assignPlayerToVoiceGroup(ServerPlayer player, VoicechatConnection connection) {
        if (!isPlayerVoiceEligible(player)) {
            connection.setGroup(null);
            return;
        }

        ensureVoiceGroups();
        TeamId team = TeamMatchManager.getTeam(player);
        Group group = team == null ? null : teamGroups.get(team.ordinal());
        connection.setGroup(group);
    }

    private static synchronized void ensureVoiceGroups() {
        VoicechatServerApi currentApi = api;
        if (currentApi == null) return;

        int groupCount = Math.min(MAX_VOICE_TEAMS, TeamId.values().length);
        for (int teamIndex = 0; teamIndex < groupCount; teamIndex++) {
            if (teamGroups.containsKey(teamIndex)) continue;

            try {
                Group group = currentApi.groupBuilder()
                        .setName("DW Team " + (teamIndex + 1))
                        .setType(Group.Type.ISOLATED)
                        .setHidden(true)
                        .setPersistent(false)
                        .build();
                teamGroups.put(teamIndex, group);
            } catch (RuntimeException exception) {
                TacticalTabletMod.LOGGER.error("Failed to create voice group for team {}", teamIndex + 1, exception);
            }
        }
    }

    private static ServerPlayer resolvePlayer(VoicechatConnection connection) {
        if (connection == null || connection.getPlayer() == null) return null;

        Object nativePlayer = connection.getPlayer().getPlayer();
        if (nativePlayer instanceof ServerPlayer player) {
            return player;
        }

        MinecraftServer server = matchServer;
        return server == null ? null : server.getPlayerList().getPlayer(connection.getPlayer().getUuid());
    }
}
