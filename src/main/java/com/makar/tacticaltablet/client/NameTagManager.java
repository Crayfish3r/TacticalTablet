package com.makar.tacticaltablet.client;

import com.makar.tacticaltablet.game.GameStateManager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps player name tags hidden without placing every player into one shared team.
 * Private one-player teams prevent team-based weapon logic from treating all players as allies.
 */
public final class NameTagManager {

    private static final String LEGACY_SHARED_TEAM_NAME = "war_hidden_names";
    private static final String PRIVATE_TEAM_PREFIX = "ttn_";
    private static final int PRIVATE_TEAM_HASH_LENGTH = 12;

    private NameTagManager() {
    }

    public static void applyToAll(MinecraftServer server) {
        if (server == null) return;

        Scoreboard scoreboard = server.getScoreboard();
        Set<String> activeTeamNames = new HashSet<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!shouldHideName(player)) {
                removeFromManagedTeam(scoreboard, player);
                continue;
            }

            String teamName = privateTeamName(player);
            activeTeamNames.add(teamName);
            applyToPlayer(scoreboard, player, teamName);
        }

        cleanupLegacySharedTeam(scoreboard);
        cleanupInactivePrivateTeams(scoreboard, activeTeamNames);
    }

    public static void apply(ServerPlayer player) {
        if (player == null || player.server == null) return;

        Scoreboard scoreboard = player.server.getScoreboard();
        if (!shouldHideName(player)) {
            removeFromManagedTeam(scoreboard, player);
            cleanupLegacySharedTeam(scoreboard);
            return;
        }

        applyToPlayer(scoreboard, player, privateTeamName(player));
        cleanupLegacySharedTeam(scoreboard);
    }

    public static void remove(ServerPlayer player) {
        if (player == null || player.server == null) return;

        Scoreboard scoreboard = player.server.getScoreboard();
        removeFromManagedTeam(scoreboard, player);

        PlayerTeam privateTeam = scoreboard.getPlayerTeam(privateTeamName(player));
        if (privateTeam != null && privateTeam.getPlayers().isEmpty()) {
            scoreboard.removePlayerTeam(privateTeam);
        }

        cleanupLegacySharedTeam(scoreboard);
    }

    private static void applyToPlayer(Scoreboard scoreboard, ServerPlayer player, String teamName) {
        String playerName = player.getScoreboardName();
        PlayerTeam privateTeam = scoreboard.getPlayerTeam(teamName);

        if (privateTeam == null) {
            privateTeam = scoreboard.addPlayerTeam(teamName);
        }

        configurePrivateTeam(privateTeam);

        PlayerTeam currentTeam = scoreboard.getPlayersTeam(playerName);
        if (currentTeam != null && currentTeam != privateTeam) {
            scoreboard.removePlayerFromTeam(playerName, currentTeam);
        }

        if (!privateTeam.getPlayers().contains(playerName)) {
            scoreboard.addPlayerToTeam(playerName, privateTeam);
        }
    }

    private static boolean shouldHideName(ServerPlayer player) {
        if (player == null || player.server == null) return false;
        if (!GameStateManager.isRunning(player.server)) return false;
        if (GameStateManager.getCurrentMode().isTeamMode()) return false;

        return player.getTags().contains("war.playing")
                || player.getTags().contains("in_lobby")
                || GameStateManager.isInLobby(player);
    }

    private static void removeFromManagedTeam(Scoreboard scoreboard, ServerPlayer player) {
        String playerName = player.getScoreboardName();
        PlayerTeam currentTeam = scoreboard.getPlayersTeam(playerName);

        if (isManagedTeam(currentTeam)) {
            scoreboard.removePlayerFromTeam(playerName, currentTeam);
        }
    }

    private static void configurePrivateTeam(PlayerTeam team) {
        team.setNameTagVisibility(Team.Visibility.NEVER);
        team.setDeathMessageVisibility(Team.Visibility.ALWAYS);
        team.setCollisionRule(Team.CollisionRule.ALWAYS);
        team.setAllowFriendlyFire(true);
        team.setSeeFriendlyInvisibles(false);
    }

    private static void cleanupLegacySharedTeam(Scoreboard scoreboard) {
        PlayerTeam legacyTeam = scoreboard.getPlayerTeam(LEGACY_SHARED_TEAM_NAME);
        if (legacyTeam == null) return;

        for (String playerName : new ArrayList<>(legacyTeam.getPlayers())) {
            scoreboard.removePlayerFromTeam(playerName, legacyTeam);
        }

        if (legacyTeam.getPlayers().isEmpty()) {
            scoreboard.removePlayerTeam(legacyTeam);
        }
    }

    private static void cleanupInactivePrivateTeams(Scoreboard scoreboard, Set<String> activeTeamNames) {
        for (PlayerTeam team : new ArrayList<>(scoreboard.getPlayerTeams())) {
            String teamName = team.getName();

            if (!teamName.startsWith(PRIVATE_TEAM_PREFIX)) {
                continue;
            }

            if (!activeTeamNames.contains(teamName) && team.getPlayers().isEmpty()) {
                scoreboard.removePlayerTeam(team);
            }
        }
    }

    private static boolean isManagedTeam(PlayerTeam team) {
        if (team == null) return false;

        String teamName = team.getName();
        return LEGACY_SHARED_TEAM_NAME.equals(teamName) || teamName.startsWith(PRIVATE_TEAM_PREFIX);
    }

    private static String privateTeamName(ServerPlayer player) {
        String normalizedName = player.getScoreboardName().toLowerCase();
        UUID id = UUID.nameUUIDFromBytes(("tacticaltablet:hidden-team:" + normalizedName)
                .getBytes(StandardCharsets.UTF_8));
        String hash = id.toString().replace("-", "").substring(0, PRIVATE_TEAM_HASH_LENGTH);

        return PRIVATE_TEAM_PREFIX + hash;
    }
}

