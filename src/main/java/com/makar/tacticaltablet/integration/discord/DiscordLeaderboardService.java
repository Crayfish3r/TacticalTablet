package com.makar.tacticaltablet.integration.discord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.makar.tacticaltablet.clan.ClanManager;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MapSetManager;
import com.makar.tacticaltablet.game.MatchMode;
import com.makar.tacticaltablet.game.team.TeamId;
import com.makar.tacticaltablet.game.team.TeamMatchManager;
import com.makar.tacticaltablet.game.set.SetResultService;
import com.makar.tacticaltablet.game.set.SetRewardSummary;
import com.makar.tacticaltablet.game.set.GamePerformance;
import com.makar.tacticaltablet.game.set.MatchPlacementTracker;
import com.makar.tacticaltablet.game.set.SetLeaderboardSnapshot;
import com.makar.tacticaltablet.game.set.SetMatchRuntime;
import com.makar.tacticaltablet.game.set.SetPlayerResult;
import com.makar.tacticaltablet.game.set.SetScoringRules;
import com.makar.tacticaltablet.game.set.SetDiscordFormatter;
import com.makar.tacticaltablet.integration.discord.DiscordWebhookClient.DiscordEmbed;
import com.makar.tacticaltablet.map.MapRotationManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.stats.PlayerStats;
import com.makar.tacticaltablet.stats.PlayerStatsRepository;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class DiscordLeaderboardService {

    private static final int OVERALL_COLOR = 0xF1C40F;
    private static final int MATCH_COLOR = 0xE74C3C;
    private static final int RANKED_MATCH_COLOR = 0x9B59B6;
    private static final int CLAN_WAR_COLOR = 0x2ECC71;
    private static final int CLAN_WAR_WIN_CLAN_COINS = 10;
    private static final int SET_STATS_DATA_VERSION = 3;
    private static final String SET_STATS_FILE = "map_set_stats.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<String, MatchStats> currentMatchStats = new ConcurrentHashMap<>();
    private static final Map<String, MatchStats> currentSetStats = new HashMap<>();
    private static final Map<String, List<GamePerformance>> currentSetGames = new HashMap<>();

    private static long currentMatchStartedAtMillis;
    private static int currentMatchAirdrops;
    private static long currentSetStartedAtMillis;
    private static int currentSetAirdrops;
    private static int currentSetMatches;
    private static boolean currentSetClanWar;
    private static boolean currentSetCompetitive;
    private static Path setStatsPath;
    private static String currentSetMap = "";

    private DiscordLeaderboardService() {
    }

    public static void init(MinecraftServer server) {
        DiscordConfig.get(server);
        loadSetState(server);
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
        if (currentSetStartedAtMillis <= 0L) {
            currentSetStartedAtMillis = currentMatchStartedAtMillis;
            currentSetClanWar = MapSetManager.isClanWarSet();
            currentSetCompetitive = MapSetManager.isCompetitiveSet();
        }

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

        currentMatchStats.computeIfAbsent(key(player), ignored -> MatchStats.fromPlayer(player)).addKill();
    }

    public static synchronized void recordMatchDeath(ServerPlayer player) {
        if (player == null) {
            return;
        }

        currentMatchStats.computeIfAbsent(key(player), ignored -> MatchStats.fromPlayer(player)).addDeath();
    }

    public static synchronized void recordMatchAssist(UUID playerId, String playerName) {
        if (playerId == null) return;
        currentMatchStats.computeIfAbsent(playerId.toString(),
                ignored -> new MatchStats(playerId.toString(), playerName)).addAssist();
    }

    public static void recordMatchDamage(ServerPlayer attacker, double amount) {
        if (attacker == null || amount <= 0.0D) {
            return;
        }

        currentMatchStats
                .computeIfAbsent(key(attacker), ignored -> MatchStats.fromPlayer(attacker))
                .addDamage(amount);
    }

    public static synchronized int recordTeamKill(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) {
            return 0;
        }

        String key = key(player);
        MatchStats setStats = currentSetStats.computeIfAbsent(key, ignored -> MatchStats.fromPlayer(player));
        setStats.teamKills++;
        if (currentSetMap == null || currentSetMap.isBlank()) {
            currentSetMap = currentMapName(server);
        }
        saveSetState(server);
        return setStats.teamKills;
    }

    public static synchronized void recordMatchAirdropStarted() {
        if (currentMatchStartedAtMillis > 0L) {
            currentMatchAirdrops++;
        }
    }

    public static synchronized SetRewardSummary sendCurrentMatchLeaderboard(
            MinecraftServer server,
            ServerPlayer winner,
            boolean setComplete,
            boolean clanWarSet
    ) {
        return sendCurrentMatchLeaderboard(
                server,
                winner == null ? List.of() : List.of(winner),
                setComplete,
                clanWarSet
        );
    }

    public static synchronized SetRewardSummary sendCurrentMatchLeaderboard(
            MinecraftServer server,
            List<ServerPlayer> winners,
            boolean setComplete,
            boolean clanWarSet
    ) {
        boolean effectiveClanWarSet = clanWarSet || currentSetClanWar;

        if (currentMatchStats.isEmpty() && !setComplete) {
            return null;
        }

        if (winners != null) {
            for (ServerPlayer winner : winners) {
                if (winner != null) {
                    currentMatchStats.computeIfAbsent(key(winner), ignored -> MatchStats.fromPlayer(winner)).addWin();
                }
            }
        }

        List<MatchPlayerStatsSnapshot> finalizedSnapshot = finalizeMatchStatistics(server);
        List<MatchStats> matchSnapshot = finalizedSnapshot.stream()
                .map(MatchStats::fromSnapshot)
                .toList();

        Map<UUID, MatchPlacementTracker.CombatTotals> combatTotals = new HashMap<>();
        for (MatchPlayerStatsSnapshot stats : finalizedSnapshot) {
            if (stats.playerId() != null) {
                combatTotals.put(stats.playerId(), new MatchPlacementTracker.CombatTotals(
                        stats.kills(), stats.assists(), stats.actualHealthDamage()));
            }
        }
        List<UUID> winnerIds = winners == null ? List.of() : winners.stream()
                .filter(java.util.Objects::nonNull).map(ServerPlayer::getUUID).distinct().toList();
        TeamId winningTeam = winners == null ? null : winners.stream()
                .filter(java.util.Objects::nonNull).map(TeamMatchManager::getTeam)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        MatchPlacementTracker.PlacementSnapshot placementSnapshot = SetMatchRuntime.finishMatch(
                winnerIds, winningTeam, combatTotals);
        List<GamePerformance> gamePerformances = buildGamePerformances(
                currentSetMatches + 1, placementSnapshot, matchSnapshot);
        for (GamePerformance performance : gamePerformances) {
            currentSetGames.computeIfAbsent(performance.playerId().toString(), ignored -> new ArrayList<>())
                    .add(performance);
        }

        for (MatchStats stats : matchSnapshot) {
            currentSetStats.computeIfAbsent(stats.uuid, ignored -> new MatchStats(
                            stats.uuid,
                            stats.name
                    ))
                    .merge(stats);
        }
        currentSetAirdrops += currentMatchAirdrops;
        currentSetMatches++;
        currentSetMap = currentMapName(server);
        saveSetState(server);

        currentMatchStats.clear();
        currentMatchStartedAtMillis = 0L;
        currentMatchAirdrops = 0;
        SetMatchRuntime.reset();

        if (!setComplete) {
            return null;
        }

        if (effectiveClanWarSet) {
            ClanWarStats clanWinner = findClanWarSetWinner(server);
            if (clanWinner != null) {
                ClanManager.addClanCoins(server, clanWinner.clanId, CLAN_WAR_WIN_CLAN_COINS);
            }
            sendClanWarSetReport(server, clanWinner);
            return null;
        }

        return prepareCompletedSetSummary(server);
    }

    public static synchronized SetRewardSummary prepareCompletedSetSummary(MinecraftServer server) {
        SetRewardSummary existing = MapSetManager.getRewardSummary();
        SetLeaderboardSnapshot existingSnapshot = MapSetManager.getLeaderboardSnapshot();
        if (existing != null && existingSnapshot != null) return existing;
        if (server == null || MapSetManager.isClanWarSet()) return null;

        SetLeaderboardSnapshot snapshot = SetResultService.createSnapshot(
                MapSetManager.getSetId(), MapSetManager.getParticipantNames(),
                SetResultService.flatten(currentSetGames));
        SetRewardSummary summary = SetResultService.createRewardSummary(snapshot);
        if (!MapSetManager.saveCompletedSetResults(server, snapshot, summary)) {
            TacticalTabletMod.LOGGER.error("Failed to persist set reward summary for set {}", summary.setId());
            return null;
        }
        return summary;
    }

    public static synchronized CompletableFuture<Boolean> sendCompletedSetReport(
            MinecraftServer server, SetRewardSummary summary) {
        return sendCurrentSetReport(server, summary);
    }

    public static synchronized void clearCompletedSetState() {
        currentSetStats.clear();
        currentSetGames.clear();
        currentSetStartedAtMillis = 0L;
        currentSetAirdrops = 0;
        currentSetMatches = 0;
        currentSetClanWar = false;
        currentSetCompetitive = false;
        currentSetMap = "";
        deleteSetState();
    }

    static synchronized List<MatchPlayerStatsSnapshot> finalizeMatchStatistics(MinecraftServer server) {
        List<MatchPlayerStatsSnapshot> snapshot = new ArrayList<>();
        for (MatchStats stats : currentMatchStats.values()) {
            snapshot.add(stats.toSnapshot(server));
        }
        return List.copyOf(snapshot);
    }

    private static void refreshSetCoinBalances(MinecraftServer server) {
        for (MatchStats stats : currentSetStats.values()) {
            stats.refreshCoinBalance(server);
        }
    }

    private static synchronized void loadSetState(MinecraftServer server) {
        if (server == null) return;

        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path serverRoot = worldRoot.getParent() == null ? worldRoot : worldRoot.getParent();
        setStatsPath = serverRoot.resolve("tacticaltablet_data").resolve(SET_STATS_FILE);
        currentSetStats.clear();
        currentSetGames.clear();
        currentSetStartedAtMillis = 0L;
        currentSetAirdrops = 0;
        currentSetMatches = 0;
        currentSetClanWar = false;
        currentSetCompetitive = false;
        currentSetMap = "";

        if (!Files.exists(setStatsPath)) return;

        try (Reader reader = Files.newBufferedReader(setStatsPath, StandardCharsets.UTF_8)) {
            SetStatsState loaded = GSON.fromJson(reader, SetStatsState.class);
            if (loaded == null || !normalizeMapName(loaded.mapName).equals(normalizeMapName(currentMapName(server)))) {
                deleteSetState();
                return;
            }
            if (loaded.dataVersion < SET_STATS_DATA_VERSION && loaded.matches > 0) {
                TacticalTabletMod.LOGGER.warn(
                        "Active set statistics version {} has no reliable per-game placements/assists; resetting only this active set",
                        loaded.dataVersion);
                deleteSetState();
                MapSetManager.resetIncompatibleActiveSet(server);
                return;
            }

            currentSetMap = loaded.mapName == null ? "" : loaded.mapName;
            currentSetStartedAtMillis = Math.max(0L, loaded.startedAtMillis);
            currentSetAirdrops = Math.max(0, loaded.airdrops);
            currentSetMatches = Math.max(0, loaded.matches);
            currentSetClanWar = loaded.clanWarSet;
            currentSetCompetitive = loaded.competitiveSet;
            if (loaded.players != null) currentSetStats.putAll(loaded.players);
            if (loaded.games != null) currentSetGames.putAll(loaded.games);
            recoverLegacyParticipants(server);
            if (!currentSetClanWar && currentSetMatches >= MapSetManager.GAMES_PER_MAP) {
                if (!MapSetManager.ensureSetCompleted(server)) {
                    TacticalTabletMod.LOGGER.error("Failed to reconcile completed set state after statistics recovery");
                }
            }
        } catch (IOException | RuntimeException exception) {
            TacticalTabletMod.LOGGER.error("Failed to load map set stats from {}", setStatsPath, exception);
            deleteSetState();
        }
    }

    private static synchronized void saveSetState(MinecraftServer server) {
        if (server == null) return;
        if (setStatsPath == null) {
            Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path serverRoot = worldRoot.getParent() == null ? worldRoot : worldRoot.getParent();
            setStatsPath = serverRoot.resolve("tacticaltablet_data").resolve(SET_STATS_FILE);
        }

        SetStatsState snapshot = new SetStatsState();
        snapshot.dataVersion = SET_STATS_DATA_VERSION;
        snapshot.mapName = currentSetMap;
        snapshot.startedAtMillis = currentSetStartedAtMillis;
        snapshot.airdrops = currentSetAirdrops;
        snapshot.matches = currentSetMatches;
        snapshot.clanWarSet = currentSetClanWar || MapSetManager.isClanWarSet();
        snapshot.competitiveSet = currentSetCompetitive || MapSetManager.isCompetitiveSet();
        snapshot.players = new HashMap<>(currentSetStats);
        snapshot.games = new HashMap<>(currentSetGames);
        Path temp = setStatsPath.resolveSibling(setStatsPath.getFileName() + ".tmp");

        try {
            Files.createDirectories(setStatsPath.getParent());
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(snapshot, writer);
            }
            try {
                Files.move(temp, setStatsPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, setStatsPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to save map set stats to {}", setStatsPath, exception);
        }
    }

    private static synchronized void deleteSetState() {
        if (setStatsPath == null) return;
        try {
            Files.deleteIfExists(setStatsPath);
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.warn("Failed to delete completed map set stats at {}", setStatsPath, exception);
        }
    }

    private static String normalizeMapName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static List<GamePerformance> buildGamePerformances(
            int gameNumber,
            MatchPlacementTracker.PlacementSnapshot placements,
            List<MatchStats> stats
    ) {
        Map<UUID, MatchStats> byId = new HashMap<>();
        if (stats != null) {
            for (MatchStats player : stats) {
                try {
                    byId.put(UUID.fromString(player.uuid), player);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        List<GamePerformance> result = new ArrayList<>();
        if (placements != null) {
            for (MatchPlacementTracker.PlayerPlacement placement : placements.players()) {
                MatchStats player = byId.get(placement.playerId());
                if (player != null) {
                    player.placement = placement.placement();
                    player.teamId = placement.teamId() == null ? "" : placement.teamId().name();
                }
                result.add(new GamePerformance(
                        gameNumber,
                        placement.playerId(),
                        placement.playerName(),
                        placement.placement(),
                        player == null ? 0 : player.kills,
                        player == null ? 0 : player.assists,
                        player == null ? 0.0D : player.damage,
                        player == null ? 0 : player.deaths,
                        placement.teamId() == null ? "" : placement.teamId().name()
                ));
            }
        }
        result.sort(SetScoringRules.GAME_PERFORMANCE_COMPARATOR);
        return List.copyOf(result);
    }

    private static void recoverLegacyParticipants(MinecraftServer server) {
        if (!MapSetManager.getParticipants().isEmpty()) return;
        Map<UUID, String> evidencedParticipants = new HashMap<>();
        for (MatchStats stats : currentSetStats.values()) {
            if (stats.wins <= 0 && stats.kills <= 0 && stats.deaths <= 0 && stats.damage <= 0.0D) continue;
            try {
                evidencedParticipants.put(UUID.fromString(stats.uuid), stats.name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (!evidencedParticipants.isEmpty()) {
            TacticalTabletMod.LOGGER.info("Recovered {} evidenced participants from legacy set statistics",
                    evidencedParticipants.size());
            MapSetManager.recoverLegacyParticipants(server, evidencedParticipants);
        }
    }

    private static CompletableFuture<Boolean> sendCurrentSetReport(MinecraftServer server, SetRewardSummary summary) {
        DiscordConfig config = DiscordConfig.get(server);
        if (config == null || !config.hasWebhook()) {
            TacticalTabletMod.LOGGER.warn("Discord webhook is not configured. Set webhookUrl in config/tacticaltablet_discord.json");
            return CompletableFuture.completedFuture(false);
        }

        SetLeaderboardSnapshot snapshot = MapSetManager.getLeaderboardSnapshot();
        List<DiscordEmbed> embeds = buildSetEmbeds(
                (currentSetCompetitive ? "Итоги рейтингового матча" : "Итоги сета")
                        + " из " + currentSetMatches + " игр - " + config.getServerName(),
                currentMapName(server),
                currentSetStartedAtMillis,
                currentSetAirdrops,
                summary,
                snapshot
        );
        return DiscordWebhookClient.sendEmbedsAsync(config.getWebhookUrl(), embeds);
    }

    private static CompletableFuture<Boolean> sendClanWarSetReport(MinecraftServer server, ClanWarStats winner) {
        DiscordConfig config = DiscordConfig.get(server);
        if (config == null || (!config.hasClanWarWebhook() && !config.hasWebhook())) {
            TacticalTabletMod.LOGGER.warn("Discord webhook is not configured. Set clanWarWebhookUrl or webhookUrl in config/tacticaltablet_discord.json");
            return CompletableFuture.completedFuture(false);
        }

        List<ClanWarStats> clans = sortedClanWarSet(buildClanWarStats(server));
        ClanWarStats damageLeader = clanWarDamageLeader(clans);
        DiscordEmbed embed = buildClanWarSetEmbed(
                "Итоги войны кланов из " + currentSetMatches + " игр - " + config.getServerName(),
                currentMapName(server),
                clans,
                config.getTopLimit(),
                currentSetStartedAtMillis,
                currentSetAirdrops,
                winner,
                damageLeader
        );

        if (config.hasClanWarWebhook()) {
            return DiscordWebhookClient.sendEmbedAsync(config.getClanWarWebhookUrl(), embed);
        }

        TacticalTabletMod.LOGGER.warn("Discord clan-war webhook is not configured. Sending green clan-war report to the regular webhook.");
        return DiscordWebhookClient.sendEmbedAsync(config.getWebhookUrl(), embed);
    }

    private static ClanWarStats findClanWarSetWinner(MinecraftServer server) {
        List<ClanWarStats> clans = sortedClanWarSet(buildClanWarStats(server));
        return clans.isEmpty() ? null : clans.get(0);
    }

    private static List<ClanWarStats> buildClanWarStats(MinecraftServer server) {
        Map<String, ClanWarStats> clans = new HashMap<>();
        if (server == null) return List.of();

        for (MatchStats stats : currentSetStats.values()) {
            String clanId = ClanManager.getClanIdForPlayerUuid(server, stats.uuid);
            if (clanId.isBlank()) continue;

            ClanWarStats clan = clans.computeIfAbsent(clanId, ignored -> new ClanWarStats(
                    clanId,
                    ClanManager.getClanNameById(server, clanId),
                    ClanManager.getClanCoinsById(server, clanId)
            ));
            clan.merge(stats);
        }

        return new ArrayList<>(clans.values());
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

    private static List<DiscordEmbed> buildSetEmbeds(
            String title,
            String mapName,
            long startedAtMillis,
            int airdrops,
            SetRewardSummary summary,
            SetLeaderboardSnapshot snapshot
    ) {
        StringBuilder description = new StringBuilder();
        appendHeaderLine(description, "Карта", mapName);
        appendHeaderLine(description, "Длительность сета", formatDuration(startedAtMillis));
        description.append("**Игры:** `").append(currentSetMatches).append("`\n");
        description.append("**Аирдропы за сет:** `").append(Math.max(0, airdrops)).append("`\n");

        if (summary != null) {
            description.append("**Уникальные участники:** `").append(summary.participantCount()).append("`\n");
            if (!summary.placements().isEmpty()) {
                description.append("**Награждённые места:**\n");
                for (var placement : summary.placements()) {
                    description.append(placement.place()).append(". **").append(placement.playerName()).append("** — `")
                            .append(summary.rewardCoins()).append(" coins`  Очки `").append(placement.totalScore())
                            .append("`  Победы `").append(placement.wins())
                            .append("`  Убийства `").append(placement.kills()).append("`  Помощи `")
                            .append(placement.assists()).append("`  PvP-урон `")
                            .append(formatDamage(placement.damage())).append("`  Смерти `")
                            .append(placement.deaths()).append("`\n");
                }
            }
        }
        int color = currentSetCompetitive ? RANKED_MATCH_COLOR : MATCH_COLOR;
        List<String> pages = SetDiscordFormatter.formatLeaderboardPages(snapshot, 3400);
        List<DiscordEmbed> embeds = new ArrayList<>();
        embeds.add(new DiscordEmbed(title, description + pages.get(0), color, "Тактический планшет"));
        for (int i = 1; i < pages.size(); i++) {
            embeds.add(new DiscordEmbed(title + " — часть " + (i + 1), pages.get(i), color,
                    "Тактический планшет"));
        }
        return List.copyOf(embeds);
    }

    private static DiscordEmbed buildClanWarSetEmbed(
            String title,
            String mapName,
            List<ClanWarStats> clans,
            int topLimit,
            long startedAtMillis,
            int airdrops,
            ClanWarStats winner,
            ClanWarStats damageLeader
    ) {
        int count = Math.min(Math.max(1, topLimit), clans.size());
        StringBuilder description = new StringBuilder();
        appendHeaderLine(description, "Карта", mapName);
        appendHeaderLine(description, "Длительность сета", formatDuration(startedAtMillis));
        description.append("**Игры:** `").append(currentSetMatches).append("`\n");
        description.append("**Аирдропы за сет:** `").append(Math.max(0, airdrops)).append("`\n");

        if (winner != null) {
            description.append("**Победитель:** **").append(winner.name).append("**  ")
                    .append("+`").append(CLAN_WAR_WIN_CLAN_COINS).append("` КК  ")
                    .append("Победы `").append(winner.wins).append("`  ")
                    .append("Убийства `").append(winner.kills).append("`  ")
                    .append("Урон `").append(formatDamage(winner.damage)).append("`\n");
        }

        if (damageLeader != null) {
            description.append("**Топ урон:** `").append(damageLeader.name).append("`  `")
                    .append(formatDamage(damageLeader.damage)).append("`\n");
        }

        if (clans.isEmpty()) {
            description.append("\nНет данных по кланам.");
        } else {
            description.append("\n**Места кланов**\n");
            for (int i = 0; i < count; i++) {
                appendClanWarRow(description, i + 1, clans.get(i));
            }
        }

        return new DiscordEmbed(title, description.toString(), CLAN_WAR_COLOR, "Тактический планшет");
    }

    private static void appendClanWarRow(StringBuilder description, int place, ClanWarStats stats) {
        description.append('\n')
                .append("**#").append(place).append(' ').append(stats.name).append("**\n")
                .append("Победы: **`").append(stats.wins).append("`**  ")
                .append("Убийства: **`").append(stats.kills).append("`**  ")
                .append("Урон: **`").append(formatDamage(stats.damage)).append("`**  ")
                .append("Смерти: `").append(stats.deaths).append("`  ")
                .append("У/С: `").append(stats.formattedKd()).append("`  ")
                .append("КК: `").append(stats.clanCoins).append("`\n");
    }

    private static void appendMatchPlayerRow(StringBuilder description, int place, MatchStats stats) {
        description.append('\n')
                .append("**#").append(place).append(" ").append(stats.name).append("**");

        if (stats.wins > 0) {
            description.append("  `ПОБЕДА`");
        }

        description.append('\n')
                .append("Очки игры: **`").append(SetScoringRules.calculateGameScore(stats.toGamePerformance())).append("`**  ")
                .append("Убийства: **`").append(stats.kills).append("`**  ")
                .append("Помощи: **`").append(stats.assists).append("`**  ")
                .append("Урон: **`").append(formatDamage(stats.damage)).append("`**  ")
                .append("Смерти: `").append(stats.deaths).append("`  ")
                .append("У/С: `").append(stats.formattedKd()).append("`  ")
                .append("Баланс: `").append(stats.coinBalance()).append("`\n");
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
                            .append("Баланс: `0`\n");
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
                .append("Очки игры: **`").append(SetScoringRules.calculateGameScore(stats.toGamePerformance())).append("`**  ")
                .append("Убийства: **`").append(stats.kills).append("`**  ")
                .append("Помощи: **`").append(stats.assists).append("`**  ")
                .append("Урон: **`").append(formatDamage(stats.damage)).append("`**  ")
                .append("Смерти: `").append(stats.deaths).append("`  ")
                .append("У/С: `").append(stats.formattedKd()).append("`  ")
                .append("Баланс: `").append(stats.coinBalance()).append("`\n");
    }

    private static void appendHeaderLine(StringBuilder description, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        description.append("**").append(label).append(":** `").append(value).append("`\n");
    }

    private static List<MatchStats> sortedMatch(List<MatchStats> players) {
        List<MatchStats> result = new ArrayList<>(players == null ? List.of() : players);
        result.sort((left, right) -> SetScoringRules.GAME_PERFORMANCE_COMPARATOR.compare(
                left.toGamePerformance(), right.toGamePerformance()));
        return result;
    }

    private static List<ClanWarStats> sortedClanWarSet(List<ClanWarStats> clans) {
        List<ClanWarStats> result = new ArrayList<>(clans == null ? List.of() : clans);
        result.sort(Comparator
                .comparingInt((ClanWarStats stats) -> stats.wins).reversed()
                .thenComparing(Comparator.comparingInt((ClanWarStats stats) -> stats.kills).reversed())
                .thenComparing(Comparator.comparingDouble((ClanWarStats stats) -> stats.damage).reversed())
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

    private static ClanWarStats clanWarDamageLeader(List<ClanWarStats> clans) {
        ClanWarStats best = null;
        for (ClanWarStats stats : clans) {
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
        private final String uuid;
        private final String name;
        private int kills;
        private int assists;
        private int deaths;
        private int wins;
        private double damage;
        private int teamKills;
        private int coinBalance;
        private int placement;
        private String teamId = "";

        private MatchStats(String name) {
            this("", name);
        }

        private MatchStats(String uuid, String name) {
            this.uuid = uuid == null ? "" : uuid;
            this.name = sanitizeName(name);
        }

        private static MatchStats fromPlayer(ServerPlayer player) {
            return new MatchStats(player.getStringUUID(), player.getGameProfile().getName());
        }

        private static MatchStats fromSnapshot(MatchPlayerStatsSnapshot snapshot) {
            MatchStats stats = new MatchStats(
                    snapshot.playerId() == null ? "" : snapshot.playerId().toString(),
                    snapshot.playerName()
            );
            stats.kills = snapshot.kills();
            stats.assists = snapshot.assists();
            stats.deaths = snapshot.deaths();
            stats.wins = snapshot.wins();
            stats.damage = snapshot.actualHealthDamage();
            stats.teamKills = snapshot.teamKills();
            stats.coinBalance = snapshot.actualCoinBalance();
            return stats;
        }

        private synchronized void addKill() {
            kills++;
        }

        private synchronized void addDeath() {
            deaths++;
        }

        private synchronized void addAssist() {
            assists++;
        }

        private synchronized void addWin() {
            wins = 1;
        }

        private synchronized void addDamage(double amount) {
            damage += amount;
        }

        private synchronized MatchStats snapshot() {
            MatchStats copy = new MatchStats(uuid, name);
            copy.kills = kills;
            copy.assists = assists;
            copy.deaths = deaths;
            copy.wins = wins;
            copy.damage = damage;
            copy.teamKills = teamKills;
            copy.coinBalance = coinBalance;
            copy.placement = placement;
            copy.teamId = teamId;
            return copy;
        }

        private synchronized MatchPlayerStatsSnapshot toSnapshot(MinecraftServer server) {
            UUID playerId = parseUuid(uuid);
            int actualBalance = playerId == null
                    ? Math.max(0, coinBalance)
                    : PlayerProgressManager.getCoins(server, playerId, name);
            return new MatchPlayerStatsSnapshot(
                    playerId,
                    name,
                    kills,
                    assists,
                    deaths,
                    damage,
                    actualBalance,
                    wins,
                    teamKills
            );
        }

        private synchronized void refreshCoinBalance(MinecraftServer server) {
            UUID playerId = parseUuid(uuid);
            if (playerId != null) {
                coinBalance = PlayerProgressManager.getCoins(server, playerId, name);
            }
        }

        private double kd() {
            return deaths <= 0 ? kills : (double) kills / deaths;
        }

        private String formattedKd() {
            return String.format(Locale.US, "%.2f", kd());
        }

        private int coinBalance() {
            return Math.max(0, coinBalance);
        }

        private PlayerStats toPlayerStats() {
            return new PlayerStats(name, kills, deaths, wins, 1, coinBalance());
        }

        private GamePerformance toGamePerformance() {
            UUID playerId = parseUuid(uuid);
            return new GamePerformance(0, playerId == null ? new UUID(0L, 0L) : playerId, name,
                    placement, kills, assists, damage, deaths, teamId);
        }

        private synchronized void merge(MatchStats other) {
            if (other == null) return;
            kills += other.kills;
            assists += other.assists;
            deaths += other.deaths;
            wins += other.wins;
            damage += other.damage;
            teamKills += other.teamKills;
            coinBalance = other.coinBalance;
        }

        private static UUID parseUuid(String value) {
            if (value == null || value.isBlank()) return null;
            try {
                return UUID.fromString(value);
            } catch (RuntimeException exception) {
                return null;
            }
        }

        private static String sanitizeName(String value) {
            if (value == null || value.isBlank()) {
                return "unknown";
            }

            return value.replace('\n', '_').replace('\r', '_').replace('`', '\'').trim();
        }
    }

    private static final class ClanWarStats {
        private final String clanId;
        private final String name;
        private final int clanCoins;
        private int kills;
        private int deaths;
        private int wins;
        private double damage;

        private ClanWarStats(String clanId, String name, int clanCoins) {
            this.clanId = clanId == null ? "" : clanId;
            this.name = MatchStats.sanitizeName(name == null || name.isBlank() ? "unknown" : name);
            this.clanCoins = Math.max(0, clanCoins);
        }

        private void merge(MatchStats other) {
            if (other == null) return;
            kills += other.kills;
            deaths += other.deaths;
            wins += other.wins;
            damage += other.damage;
        }

        private double kd() {
            return deaths <= 0 ? kills : (double) kills / deaths;
        }

        private String formattedKd() {
            return String.format(Locale.US, "%.2f", kd());
        }
    }

    private static final class SetStatsState {
        int dataVersion = SET_STATS_DATA_VERSION;
        String mapName = "";
        long startedAtMillis;
        int airdrops;
        int matches;
        boolean clanWarSet;
        boolean competitiveSet;
        Map<String, MatchStats> players = new HashMap<>();
        Map<String, List<GamePerformance>> games = new HashMap<>();
    }

}
