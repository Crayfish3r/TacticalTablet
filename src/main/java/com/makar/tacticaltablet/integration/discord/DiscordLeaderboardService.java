package com.makar.tacticaltablet.integration.discord;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MatchMode;
import com.makar.tacticaltablet.game.team.TeamId;
import com.makar.tacticaltablet.game.team.TeamMatchManager;
import com.makar.tacticaltablet.integration.discord.DiscordWebhookClient.DiscordEmbed;
import com.makar.tacticaltablet.map.MapRotationManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.stats.PlayerStats;
import com.makar.tacticaltablet.stats.PlayerStatsRepository;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class DiscordLeaderboardService {

    private static final int OVERALL_COLOR = 0xF1C40F;
    private static final int MATCH_COLOR = 0xE74C3C;
    private static final Map<String, MatchStats> currentMatchStats = new HashMap<>();

    private static long currentMatchStartedAtMillis;
    private static int currentMatchAirdrops;

    private DiscordLeaderboardService() {
    }

    public static void init(MinecraftServer server) {
        DiscordConfig.get(server);
    }

    public static CompletableFuture<Boolean> sendOverallLeaderboard(MinecraftServer server) {
        DiscordConfig config = DiscordConfig.get(server);
        List<PlayerStats> players = PlayerStatsRepository.loadAll(server);
        return sendLeaderboard(
                config,
                "Общий рейтинг - " + config.getServerName(),
                players,
                config.getTopLimit(),
                OVERALL_COLOR
        );
    }

    public static CompletableFuture<Boolean> sendMatchLeaderboard(List<PlayerStats> players) {
        DiscordConfig config = DiscordConfig.get(null);
        DiscordEmbed embed = buildLegacyMatchEmbed(
                "Итоги матча - " + config.getServerName(),
                MatchMode.SOLO,
                "",
                "",
                players,
                config.getTopLimit(),
                MATCH_COLOR
        );
        return DiscordWebhookClient.sendEmbedAsync(config.getWebhookUrl(), embed);
    }

    public static CompletableFuture<Boolean> sendMatchLeaderboard(MinecraftServer server, List<PlayerStats> players) {
        DiscordConfig config = DiscordConfig.get(server);
        DiscordEmbed embed = buildLegacyMatchEmbed(
                "Итоги матча - " + config.getServerName(),
                GameStateManager.getCurrentMode(),
                currentMapName(server),
                "",
                players,
                config.getTopLimit(),
                MATCH_COLOR
        );
        return DiscordWebhookClient.sendEmbedAsync(config.getWebhookUrl(), embed);
    }

    public static synchronized void startMatch(MinecraftServer server) {
        currentMatchStats.clear();
        currentMatchStartedAtMillis = System.currentTimeMillis();
        currentMatchAirdrops = 0;

        if (server == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            currentMatchStats.put(key(player), MatchStats.fromPlayer(player));
        }
    }

    public static synchronized void resetMatch() {
        currentMatchStats.clear();
        currentMatchStartedAtMillis = 0L;
        currentMatchAirdrops = 0;
    }

    public static synchronized void recordMatchKill(ServerPlayer player) {
        if (player == null) {
            return;
        }

        currentMatchStats.computeIfAbsent(key(player), ignored -> MatchStats.fromPlayer(player)).kills++;
    }

    public static synchronized void recordMatchDeath(ServerPlayer player) {
        if (player == null) {
            return;
        }

        currentMatchStats.computeIfAbsent(key(player), ignored -> MatchStats.fromPlayer(player)).deaths++;
    }

    public static synchronized void recordMatchDamage(ServerPlayer attacker, double amount) {
        if (attacker == null || amount <= 0.0D) {
            return;
        }

        currentMatchStats.computeIfAbsent(key(attacker), ignored -> MatchStats.fromPlayer(attacker)).damage += amount;
    }

    public static synchronized void recordMatchAirdropStarted() {
        if (currentMatchStartedAtMillis > 0L) {
            currentMatchAirdrops++;
        }
    }

    public static synchronized void sendCurrentMatchLeaderboard(MinecraftServer server, ServerPlayer winner) {
        if (currentMatchStats.isEmpty()) {
            return;
        }

        MatchMode mode = GameStateManager.getCurrentMode();
        if (mode == null) {
            mode = MatchMode.SOLO;
        }
        Map<TeamId, List<String>> teams = mode.isTeamMode() ? TeamMatchManager.teamNameSnapshot(server) : Map.of();
        String winnerName = "";

        if (winner != null) {
            currentMatchStats.computeIfAbsent(key(winner), ignored -> MatchStats.fromPlayer(winner)).wins = 1;
            winnerName = winner.getGameProfile().getName();
        }

        if (mode.isTeamMode()) {
            TeamId winningTeam = TeamMatchManager.findWinningTeam(server);
            if (winningTeam != null) {
                winnerName = winningTeam.displayName();
            }
        }

        List<MatchStats> players = new ArrayList<>(currentMatchStats.values());
        sendMatchReport(server, players, winnerName, currentMatchStartedAtMillis, currentMatchAirdrops, mode, teams);

        currentMatchStats.clear();
        currentMatchStartedAtMillis = 0L;
        currentMatchAirdrops = 0;
    }

    public static List<PlayerStats> sorted(List<PlayerStats> players) {
        List<PlayerStats> result = new ArrayList<>(players == null ? List.of() : players);
        result.sort(Comparator
                .comparingInt(PlayerStats::getKills).reversed()
                .thenComparing(Comparator.comparingDouble(PlayerStats::getKd).reversed())
                .thenComparing(Comparator.comparingInt(PlayerStats::getWins).reversed())
                .thenComparing(PlayerStats::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static CompletableFuture<Boolean> sendLeaderboard(
            DiscordConfig config,
            String title,
            List<PlayerStats> players,
            int topLimit,
            int color
    ) {
        if (config == null || !config.hasWebhook()) {
            TacticalTabletMod.LOGGER.warn("Discord webhook is not configured. Set webhookUrl in config/tacticaltablet_discord.json");
            return CompletableFuture.completedFuture(false);
        }

        DiscordEmbed message = buildOverallEmbed(title, players, topLimit, color);
        return DiscordWebhookClient.sendEmbedAsync(config.getWebhookUrl(), message);
    }

    private static CompletableFuture<Boolean> sendMatchReport(
            MinecraftServer server,
            List<MatchStats> players,
            String winnerName,
            long startedAtMillis,
            int airdrops,
            MatchMode mode,
            Map<TeamId, List<String>> teams
    ) {
        DiscordConfig config = DiscordConfig.get(server);
        if (config == null || !config.hasWebhook()) {
            TacticalTabletMod.LOGGER.warn("Discord webhook is not configured. Set webhookUrl in config/tacticaltablet_discord.json");
            return CompletableFuture.completedFuture(false);
        }

        MatchMode safeMode = mode == null ? MatchMode.SOLO : mode;
        List<MatchStats> sorted = sortedMatch(players);
        MatchStats damageLeader = damageLeader(sorted);
        DiscordEmbed embed = buildMatchEmbed(
                "Итоги матча - " + config.getServerName(),
                currentMapName(server),
                winnerName,
                sorted,
                config.getTopLimit(),
                startedAtMillis,
                airdrops,
                damageLeader,
                safeMode,
                teams
        );

        return DiscordWebhookClient.sendEmbedAsync(config.getWebhookUrl(), embed);
    }

    private static Map<String, MatchStats> matchStatsByName(List<MatchStats> players) {
        Map<String, MatchStats> result = new HashMap<>();
        if (players == null) {
            return result;
        }

        for (MatchStats stats : players) {
            if (stats != null) {
                result.put(normalizeStatsName(stats.name), stats);
            }
        }

        return result;
    }

    private static DiscordEmbed buildOverallEmbed(String title, List<PlayerStats> players, int topLimit, int color) {
        List<PlayerStats> sorted = sorted(players);
        int count = Math.min(Math.max(1, topLimit), sorted.size());
        StringBuilder description = new StringBuilder();

        if (sorted.isEmpty()) {
            description.append("Нет данных.");
        } else {
            description.append("**Рейтинг игроков**\n");

            for (int i = 0; i < count; i++) {
                PlayerStats stats = sorted.get(i);
                description.append('\n')
                        .append("**#").append(i + 1).append(" ").append(stats.getName()).append("**\n")
                        .append("Убийства: **`").append(stats.getKills()).append("`**  ")
                        .append("Смерти: `").append(stats.getDeaths()).append("`  ")
                        .append("У/С: `").append(stats.getFormattedKd()).append("`\n")
                        .append("Победы: `").append(stats.getWins()).append("`  ")
                        .append("Матчи: `").append(stats.getMatchesPlayed()).append("`  ")
                        .append("Монеты: `").append(stats.getCoins()).append("`\n");
            }
        }

        return new DiscordEmbed(title, description.toString(), color, "Тактический планшет");
    }

    private static DiscordEmbed buildLegacyMatchEmbed(
            String title,
            MatchMode mode,
            String mapName,
            String winnerName,
            List<PlayerStats> players,
            int topLimit,
            int color
    ) {
        List<PlayerStats> sorted = sorted(players);
        int count = Math.min(Math.max(1, topLimit), sorted.size());
        StringBuilder description = new StringBuilder();

        appendHeaderLine(description, "Режим", (mode == null ? MatchMode.SOLO : mode).displayName());
        appendHeaderLine(description, "Карта", mapName);
        appendHeaderLine(description, "Победитель", winnerName);

        if (sorted.isEmpty()) {
            description.append("\nНет данных по матчу.");
        } else {
            description.append("\n**Результаты игроков**\n");

            for (int i = 0; i < count; i++) {
                PlayerStats stats = sorted.get(i);
                description.append('\n')
                        .append("**#").append(i + 1).append(" ").append(stats.getName()).append("**\n")
                        .append("Убийства: `").append(stats.getKills()).append("`  ")
                        .append("Смерти: `").append(stats.getDeaths()).append("`  ")
                        .append("У/С: `").append(stats.getFormattedKd()).append("`  ")
                        .append("Монеты: `").append(stats.getCoins()).append("`\n");
            }
        }

        return new DiscordEmbed(title, description.toString(), color, "Тактический планшет");
    }

    private static DiscordEmbed buildMatchEmbed(
            String title,
            String mapName,
            String winnerName,
            List<MatchStats> players,
            int topLimit,
            long startedAtMillis,
            int airdrops,
            MatchStats damageLeader,
            MatchMode mode,
            Map<TeamId, List<String>> teams
    ) {
        MatchMode safeMode = mode == null ? MatchMode.SOLO : mode;
        int count = Math.min(Math.max(1, topLimit), players.size());
        StringBuilder description = new StringBuilder();

        appendHeaderLine(description, "Режим", safeMode.displayName());
        appendHeaderLine(description, "Карта", mapName);
        appendHeaderLine(description, "Длительность", formatDuration(startedAtMillis));
        description.append("**Сбросов за матч:** `").append(Math.max(0, airdrops)).append("`\n");

        if (winnerName != null && !winnerName.isBlank()) {
            MatchStats winner = findMatchPlayer(players, winnerName);
            description.append("**Победитель:** **").append(winnerName).append("**");
            if (winner != null) {
                description.append("  Убийства `").append(winner.kills).append("`")
                        .append("  Урон `").append(formatDamage(winner.damage)).append("`");
            }
            description.append('\n');
        }

        if (damageLeader != null) {
            description.append("**Топ урон:** `").append(damageLeader.name).append("`  `")
                    .append(formatDamage(damageLeader.damage)).append("`\n");
        }

        if (players.isEmpty()) {
            description.append("\nНет данных по матчу.");
        } else if (safeMode.isTeamMode()) {
            appendTeamMatchResults(description, teams, players);
        } else {
            description.append("\n**Результаты игроков**\n");

            for (int i = 0; i < count; i++) {
                appendMatchPlayerRow(description, i + 1, players.get(i));
            }
        }

        return new DiscordEmbed(title, description.toString(), MATCH_COLOR, "Тактический планшет");
    }

    private static void appendMatchPlayerRow(StringBuilder description, int place, MatchStats stats) {
        description.append('\n')
                .append("**#").append(place).append(" ").append(stats.name).append("**");

        if (stats.wins > 0) {
            description.append("  `ПОБЕДА`");
        }

        description.append('\n')
                .append("Убийства: **`").append(stats.kills).append("`**  ")
                .append("Урон: **`").append(formatDamage(stats.damage)).append("`**  ")
                .append("Смерти: `").append(stats.deaths).append("`  ")
                .append("У/С: `").append(stats.formattedKd()).append("`  ")
                .append("Монеты: `").append(stats.coinsEarned()).append("`\n");
    }

    private static void appendTeamMatchResults(
            StringBuilder description,
            Map<TeamId, List<String>> teams,
            List<MatchStats> players
    ) {
        Map<String, MatchStats> statsByName = matchStatsByName(players);
        boolean anyTeam = false;

        description.append("\n**Команды**\n");

        for (TeamId teamId : TeamId.values()) {
            List<String> members = teams == null ? List.of() : teams.getOrDefault(teamId, List.of());
            if (members.isEmpty()) {
                continue;
            }

            anyTeam = true;
            description.append('\n').append("**").append(teamId.displayName()).append("**\n");
            for (String name : members) {
                MatchStats stats = statsByName.get(normalizeStatsName(name));
                if (stats == null) {
                    description.append("**").append(safeDisplayName(name)).append("**\n")
                            .append("Убийства: **`0`**  ")
                            .append("Урон: **`0.0`**  ")
                            .append("Смерти: `0`  ")
                            .append("У/С: `0.00`  ")
                            .append("Монеты: `0`\n");
                } else {
                    appendTeamPlayerRow(description, stats);
                }
            }
        }

        if (!anyTeam) {
            description.append("Нет данных по командам.");
        }
    }

    private static void appendTeamPlayerRow(StringBuilder description, MatchStats stats) {
        description.append("**").append(stats.name).append("**");

        if (stats.wins > 0) {
            description.append("  `ПОБЕДА`");
        }

        description.append('\n')
                .append("Убийства: **`").append(stats.kills).append("`**  ")
                .append("Урон: **`").append(formatDamage(stats.damage)).append("`**  ")
                .append("Смерти: `").append(stats.deaths).append("`  ")
                .append("У/С: `").append(stats.formattedKd()).append("`  ")
                .append("Монеты: `").append(stats.coinsEarned()).append("`\n");
    }

    private static void appendHeaderLine(StringBuilder description, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        description.append("**").append(label).append(":** `").append(value).append("`\n");
    }

    private static List<MatchStats> sortedMatch(List<MatchStats> players) {
        List<MatchStats> result = new ArrayList<>(players == null ? List.of() : players);
        result.sort(Comparator
                .comparingInt((MatchStats stats) -> stats.kills).reversed()
                .thenComparing(Comparator.comparingDouble((MatchStats stats) -> stats.damage).reversed())
                .thenComparingInt(stats -> stats.deaths)
                .thenComparing(stats -> stats.name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static MatchStats damageLeader(List<MatchStats> players) {
        MatchStats best = null;

        for (MatchStats stats : players) {
            if (best == null || stats.damage > best.damage) {
                best = stats;
            }
        }

        return best != null && best.damage > 0.0D ? best : null;
    }

    private static MatchStats findMatchPlayer(List<MatchStats> players, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        for (MatchStats stats : players) {
            if (stats.name.equalsIgnoreCase(name)) {
                return stats;
            }
        }

        return null;
    }

    private static String normalizeStatsName(String name) {
        return safeTableName(name).toLowerCase(Locale.ROOT);
    }

    private static String safeDisplayName(String value) {
        return safeTableName(value);
    }

    private static String safeTableName(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }

        return value.replace('\n', '_').replace('\r', '_').replace('`', '\'').trim();
    }

    private static String formatDuration(long startedAtMillis) {
        if (startedAtMillis <= 0L) {
            return "неизвестно";
        }

        long totalSeconds = Math.max(0L, (System.currentTimeMillis() - startedAtMillis) / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%d мин %02d сек", minutes, seconds);
    }

    private static String formatDamage(double damage) {
        return String.format(Locale.US, "%.1f", Math.max(0.0D, damage));
    }

    private static String key(ServerPlayer player) {
        return player.getStringUUID();
    }

    private static String currentMapName(MinecraftServer server) {
        if (server == null) {
            return "";
        }

        try {
            MapRotationManager.RotationStatus status = MapRotationManager.getStatus(server);
            if (status.currentMap() != null && !status.currentMap().isBlank()) {
                return status.currentMap();
            }
        } catch (RuntimeException exception) {
            TacticalTabletMod.LOGGER.warn("Failed to read map name for Discord match leaderboard", exception);
        }

        try {
            return server.getWorldPath(LevelResource.ROOT).getFileName().toString();
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private static final class MatchStats {
        private final String name;
        private int kills;
        private int deaths;
        private int wins;
        private double damage;

        private MatchStats(String name) {
            this.name = sanitizeName(name);
        }

        private static MatchStats fromPlayer(ServerPlayer player) {
            return new MatchStats(player.getGameProfile().getName());
        }

        private double kd() {
            return deaths <= 0 ? kills : (double) kills / deaths;
        }

        private String formattedKd() {
            return String.format(Locale.US, "%.2f", kd());
        }

        private int coinsEarned() {
            return kills * PlayerProgressManager.KILL_COIN_REWARD
                    + wins * PlayerProgressManager.WIN_COIN_REWARD;
        }

        private PlayerStats toPlayerStats() {
            return new PlayerStats(name, kills, deaths, wins, 1, coinsEarned());
        }

        private static String sanitizeName(String value) {
            if (value == null || value.isBlank()) {
                return "unknown";
            }

            return value.replace('\n', '_').replace('\r', '_').replace('`', '\'').trim();
        }
    }
}
