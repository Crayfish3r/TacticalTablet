package com.makar.tacticaltablet.game.team;

import com.makar.tacticaltablet.game.MatchMode;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class TeamMatchManager {

    private static final int TEAM_SELECT_SECONDS = 12;
    private static final Random RANDOM = new Random();
    private static final Map<TeamId, LinkedHashSet<UUID>> teams = new EnumMap<>(TeamId.class);
    private static final Map<UUID, TeamId> playerTeams = new HashMap<>();
    private static final Map<UUID, String> playerNames = new HashMap<>();
    private static final Map<UUID, String> originalScoreboardTeams = new HashMap<>();
    private static int secondsLeft = 0;
    private static boolean activeSelection = false;

    private TeamMatchManager() {
    }

    public static void startSelection(MatchMode mode) {
        clearAssignments();
        for (TeamId team : TeamId.values()) {
            teams.put(team, new LinkedHashSet<>());
        }
        secondsLeft = TEAM_SELECT_SECONDS;
        activeSelection = mode != null && mode.isTeamMode();
    }

    public static void reset(MinecraftServer server) {
        cleanupScoreboardTeams(server);
        clearAssignments();
        secondsLeft = 0;
        activeSelection = false;
    }

    public static boolean isSelectionActive() {
        return activeSelection;
    }

    public static int getSecondsLeft() {
        return Math.max(0, secondsLeft);
    }

    public static void tickSecond() {
        if (!activeSelection || secondsLeft <= 0) return;
        secondsLeft--;
    }

    public static boolean isSelectionComplete() {
        return activeSelection && secondsLeft <= 0;
    }

    public static boolean joinTeam(ServerPlayer player, TeamId team, MatchMode mode) {
        if (player == null || team == null || mode == null || !activeSelection || !mode.isTeamMode()) return false;

        LinkedHashSet<UUID> target = teams.computeIfAbsent(team, ignored -> new LinkedHashSet<>());
        UUID uuid = player.getUUID();
        TeamId currentTeam = playerTeams.get(uuid);

        if (currentTeam == team) {
            rememberName(player);
            return true;
        }

        if (target.size() >= mode.teamSize()) {
            return false;
        }

        removeAssignment(uuid);
        target.add(uuid);
        playerTeams.put(uuid, team);
        rememberName(player);
        return true;
    }

    public static void rememberPlayer(ServerPlayer player) {
        if (player == null) return;
        rememberName(player);
    }

    public static boolean isInTeamMode(MatchMode mode) {
        return mode != null && mode.isTeamMode() && !playerTeams.isEmpty();
    }

    public static TeamId getTeam(ServerPlayer player) {
        return player == null ? null : playerTeams.get(player.getUUID());
    }

    public static List<ServerPlayer> getOnlineTeamMembers(MinecraftServer server, TeamId teamId) {
        List<ServerPlayer> result = new ArrayList<>();
        if (server == null || teamId == null) return result;

        for (UUID uuid : teams.getOrDefault(teamId, new LinkedHashSet<>())) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                result.add(player);
            }
        }
        return result;
    }

    public static boolean areTeammates(ServerPlayer first, ServerPlayer second) {
        if (first == null || second == null) return false;
        TeamId firstTeam = playerTeams.get(first.getUUID());
        return firstTeam != null && firstTeam == playerTeams.get(second.getUUID());
    }

    public static void autoBalance(MinecraftServer server, MatchMode mode) {
        if (server == null || mode == null || !mode.isTeamMode()) return;

        for (TeamId team : TeamId.values()) {
            teams.computeIfAbsent(team, ignored -> new LinkedHashSet<>());
        }

        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        players.sort((a, b) -> a.getStringUUID().compareTo(b.getStringUUID()));

        for (ServerPlayer player : players) {
            rememberName(player);
            if (playerTeams.containsKey(player.getUUID())) continue;

            TeamId target = findSmallestAvailableTeam(mode);
            if (target == null) {
                target = findSmallestTeam();
            }
            if (target == null) continue;

            teams.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).add(player.getUUID());
            playerTeams.put(player.getUUID(), target);
        }

        activeSelection = false;
        secondsLeft = 0;
    }

    public static void applyScoreboardTeams(MinecraftServer server) {
        if (server == null) return;

        Scoreboard scoreboard = server.getScoreboard();
        for (TeamId teamId : TeamId.values()) {
            PlayerTeam team = scoreboard.getPlayerTeam(teamId.scoreboardName());
            if (team == null) {
                team = scoreboard.addPlayerTeam(teamId.scoreboardName());
            }
            configureTeam(teamId, team);
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TeamId teamId = playerTeams.get(player.getUUID());
            if (teamId == null) continue;

            rememberOriginalTeam(scoreboard, player);
            PlayerTeam team = scoreboard.getPlayerTeam(teamId.scoreboardName());
            if (team == null) continue;

            PlayerTeam current = scoreboard.getPlayersTeam(player.getScoreboardName());
            if (current != null && current != team) {
                scoreboard.removePlayerFromTeam(player.getScoreboardName(), current);
            }
            if (!team.getPlayers().contains(player.getScoreboardName())) {
                scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
            }
        }
    }

    public static void cleanupScoreboardTeams(MinecraftServer server) {
        if (server == null) return;

        Scoreboard scoreboard = server.getScoreboard();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerTeam current = scoreboard.getPlayersTeam(player.getScoreboardName());
            if (isManagedTeam(current)) {
                scoreboard.removePlayerFromTeam(player.getScoreboardName(), current);
            }

            String originalTeamName = originalScoreboardTeams.remove(player.getUUID());
            if (originalTeamName != null && !originalTeamName.isBlank()) {
                PlayerTeam original = scoreboard.getPlayerTeam(originalTeamName);
                if (original != null) {
                    scoreboard.addPlayerToTeam(player.getScoreboardName(), original);
                }
            }
        }

        for (TeamId teamId : TeamId.values()) {
            PlayerTeam team = scoreboard.getPlayerTeam(teamId.scoreboardName());
            if (team == null) continue;

            for (String playerName : new ArrayList<>(team.getPlayers())) {
                scoreboard.removePlayerFromTeam(playerName, team);
            }
            scoreboard.removePlayerTeam(team);
        }
        originalScoreboardTeams.clear();
    }

    public static int getAliveTeamCount(MinecraftServer server) {
        if (server == null) return 0;

        int aliveTeams = 0;
        for (TeamId teamId : TeamId.values()) {
            if (hasAliveOnlineMember(server, teamId)) {
                aliveTeams++;
            }
        }
        return aliveTeams;
    }

    public static TeamId findWinningTeam(MinecraftServer server) {
        if (server == null) return null;

        TeamId winner = null;
        for (TeamId teamId : TeamId.values()) {
            if (!hasAliveOnlineMember(server, teamId)) continue;
            if (winner != null) return null;
            winner = teamId;
        }
        return winner;
    }

    public static ServerPlayer findWinningPlayer(MinecraftServer server) {
        TeamId winner = findWinningTeam(server);
        if (winner == null) return null;

        for (UUID uuid : teams.getOrDefault(winner, new LinkedHashSet<>())) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null && com.makar.tacticaltablet.game.lives.LivesManager.isAliveParticipant(player)) {
                return player;
            }
        }
        return null;
    }

    public static Snapshot snapshot(MinecraftServer server, ServerPlayer viewer, MatchMode mode) {
        int selectedTeam = -1;
        if (viewer != null && playerTeams.containsKey(viewer.getUUID())) {
            selectedTeam = playerTeams.get(viewer.getUUID()).ordinal();
        }

        Map<String, String> slots = new HashMap<>();
        int maxSlots = mode == null ? 1 : mode.teamSize();
        for (TeamId teamId : TeamId.values()) {
            List<UUID> members = new ArrayList<>(teams.getOrDefault(teamId, new LinkedHashSet<>()));
            for (int slot = 0; slot < Math.max(maxSlots, members.size()); slot++) {
                String key = teamId.ordinal() + ":" + slot;
                String value = "";
                if (slot < members.size()) {
                    value = displayName(server, members.get(slot));
                }
                slots.put(key, value);
            }
        }

        return new Snapshot(maxSlots, selectedTeam, slots);
    }

    public static Map<TeamId, List<String>> teamNameSnapshot(MinecraftServer server) {
        Map<TeamId, List<String>> result = new EnumMap<>(TeamId.class);
        for (TeamId teamId : TeamId.values()) {
            List<String> names = new ArrayList<>();
            for (UUID uuid : teams.getOrDefault(teamId, new LinkedHashSet<>())) {
                names.add(displayName(server, uuid));
            }
            result.put(teamId, List.copyOf(names));
        }
        return result;
    }

    private static boolean hasAliveOnlineMember(MinecraftServer server, TeamId teamId) {
        Set<UUID> members = teams.get(teamId);
        if (members == null || members.isEmpty()) return false;

        for (UUID uuid : members) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null && com.makar.tacticaltablet.game.lives.LivesManager.isAliveParticipant(player)) {
                return true;
            }
        }
        return false;
    }

    private static TeamId findSmallestAvailableTeam(MatchMode mode) {
        int bestSize = Integer.MAX_VALUE;
        List<TeamId> candidates = new ArrayList<>();

        for (TeamId teamId : TeamId.values()) {
            int size = teams.computeIfAbsent(teamId, ignored -> new LinkedHashSet<>()).size();
            if (size >= mode.teamSize()) continue;

            if (size < bestSize) {
                bestSize = size;
                candidates.clear();
                candidates.add(teamId);
            } else if (size == bestSize) {
                candidates.add(teamId);
            }
        }

        return candidates.isEmpty() ? null : candidates.get(RANDOM.nextInt(candidates.size()));
    }

    private static TeamId findSmallestTeam() {
        int bestSize = Integer.MAX_VALUE;
        List<TeamId> candidates = new ArrayList<>();

        for (TeamId teamId : TeamId.values()) {
            int size = teams.computeIfAbsent(teamId, ignored -> new LinkedHashSet<>()).size();
            if (size < bestSize) {
                bestSize = size;
                candidates.clear();
                candidates.add(teamId);
            } else if (size == bestSize) {
                candidates.add(teamId);
            }
        }

        return candidates.isEmpty() ? null : candidates.get(RANDOM.nextInt(candidates.size()));
    }

    private static void removeAssignment(UUID uuid) {
        TeamId oldTeam = playerTeams.remove(uuid);
        if (oldTeam == null) return;

        LinkedHashSet<UUID> members = teams.get(oldTeam);
        if (members != null) {
            members.remove(uuid);
        }
    }

    private static void clearAssignments() {
        teams.clear();
        playerTeams.clear();
        playerNames.clear();
    }

    private static void configureTeam(TeamId teamId, PlayerTeam team) {
        team.setNameTagVisibility(Team.Visibility.HIDE_FOR_OTHER_TEAMS);
        team.setDeathMessageVisibility(Team.Visibility.ALWAYS);
        team.setCollisionRule(Team.CollisionRule.ALWAYS);
        team.setAllowFriendlyFire(true);
        team.setSeeFriendlyInvisibles(false);
        team.setColor(teamId.chatColor());
    }

    private static boolean isManagedTeam(PlayerTeam team) {
        if (team == null) return false;
        for (TeamId teamId : TeamId.values()) {
            if (teamId.scoreboardName().equals(team.getName())) {
                return true;
            }
        }
        return false;
    }

    private static void rememberOriginalTeam(Scoreboard scoreboard, ServerPlayer player) {
        if (originalScoreboardTeams.containsKey(player.getUUID())) return;

        PlayerTeam current = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (current != null && !isManagedTeam(current)) {
            originalScoreboardTeams.put(player.getUUID(), current.getName());
        } else {
            originalScoreboardTeams.put(player.getUUID(), "");
        }
    }

    private static void rememberName(ServerPlayer player) {
        playerNames.put(player.getUUID(), player.getGameProfile().getName());
    }

    private static String displayName(MinecraftServer server, UUID uuid) {
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(uuid);
            if (online != null) {
                rememberName(online);
                return online.getGameProfile().getName();
            }
        }
        return playerNames.getOrDefault(uuid, uuid.toString().substring(0, 8));
    }

    public record Snapshot(int maxSlots, int selectedTeam, Map<String, String> slots) {
    }
}
