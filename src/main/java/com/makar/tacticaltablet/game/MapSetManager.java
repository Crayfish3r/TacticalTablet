package com.makar.tacticaltablet.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.map.MapRotationManager;
import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.progression.CompetitiveClassTierPolicy;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.game.set.SetRewardSummary;
import com.makar.tacticaltablet.game.set.SetLeaderboardSnapshot;
import com.makar.tacticaltablet.tablet.net.MapVoteStatePacket;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;

public final class MapSetManager {

    public static final int GAMES_PER_MAP = 5;
    public static final int MAP_VOTE_SECONDS = 30;
    public static final int RESTART_COUNTDOWN_SECONDS = 10;
    public static final int SET_REWARD_SECONDS = 15;
    public static final int CANDIDATE_COUNT = 3;
    public static final int RECENT_MAP_COOLDOWN = 3;

    private static final int DATA_VERSION = MapSetProgressionPolicy.CURRENT_DATA_VERSION;
    private static final String STATE_FILE = "map_set_state.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Random RANDOM = new Random();
    private static List<String> votingMaps = List.of();

    private static final Map<UUID, String> votes = new HashMap<>();
    private static SetState state = new SetState();
    private static Path statePath;
    private static boolean voting;
    private static int voteSecondsLeft;
    private static int restartSecondsLeft = -1;
    private static String selectedMap = "";
    private static boolean stopIssued;

    private MapSetManager() {
    }

    public static synchronized void onServerStarted(MinecraftServer server) {
        resetRuntimeState();
        initStorage(server);
        MapRotationManager.RotationStatus rotationStatus = MapRotationManager.getStatus(server);
        String currentMap = currentMapName(server);
        String lastRotation = rotationStatus.lastRotation() == null ? "" : rotationStatus.lastRotation();

        if (state.mapName == null || state.mapName.isBlank()) {
            state.mapName = currentMap;
            state.lastRotation = lastRotation;
            state.completedGames = 0;
            state.setId = UUID.randomUUID().toString();
            state.participants.clear();
            state.rewardSummary = null;
            state.leaderboardSnapshot = null;
            state.rewardEndsAtMillis = 0L;
            state.rewardPhaseStatus = RewardPhaseStatus.PENDING;
            state.setReportDispatched = false;
            saveState();
        } else if (!normalize(state.mapName).equals(normalize(currentMap))
                || (!state.lastRotation.isBlank() && !lastRotation.isBlank()
                && !lastRotation.equals(state.lastRotation))) {
            TacticalTabletMod.LOGGER.info(
                    "Tactical Tablet detected a completed map rotation. Resetting map set: {} -> {}, competitive={}, clanWar={}",
                    state.mapName,
                    currentMap,
                    state.nextSetCompetitive,
                    state.nextSetClanWar
            );
            state.mapName = currentMap;
            state.lastRotation = lastRotation;
            state.completedGames = 0;
            state.setId = UUID.randomUUID().toString();
            state.participants.clear();
            state.rewardSummary = null;
            state.leaderboardSnapshot = null;
            state.rewardEndsAtMillis = 0L;
            state.rewardPhaseStatus = RewardPhaseStatus.PENDING;
            state.setReportDispatched = false;
            state.competitiveSet = state.nextSetCompetitive;
            state.clanWarSet = state.nextSetClanWar;
            state.nextSetCompetitive = false;
            state.nextSetClanWar = false;
            saveState();
        } else if (state.lastRotation.isBlank() && !lastRotation.isBlank()) {
            state.lastRotation = lastRotation;
            saveState();
        }
        reconcileRecentPlayedMaps(server);
    }

    public static synchronized void onServerStopped() {
        saveState();
        resetRuntimeState();
        statePath = null;
        state = new SetState();
    }

    public static synchronized int getCurrentGameNumber() {
        return MapSetProgressionPolicy.currentGameNumber(state.completedGames, GAMES_PER_MAP);
    }

    public static synchronized int getCompletedGames() {
        return MapSetProgressionPolicy.normalizeCompletedGames(state.completedGames, GAMES_PER_MAP);
    }

    public static synchronized boolean onGameCompleted(MinecraftServer server) {
        initStorage(server);
        state.completedGames = MapSetProgressionPolicy.completedAfterGame(state.completedGames, GAMES_PER_MAP);
        saveState();
        return MapSetProgressionPolicy.isComplete(state.completedGames, GAMES_PER_MAP);
    }

    public static synchronized boolean ensureSetCompleted(MinecraftServer server) {
        initStorage(server);
        if (state.completedGames >= GAMES_PER_MAP) return true;
        int previous = state.completedGames;
        state.completedGames = GAMES_PER_MAP;
        if (saveState()) return true;
        state.completedGames = previous;
        return false;
    }

    public static synchronized boolean isSetComplete() {
        return MapSetProgressionPolicy.isComplete(state.completedGames, GAMES_PER_MAP);
    }

    public static synchronized UUID getSetId() {
        try {
            return UUID.fromString(state.setId);
        } catch (RuntimeException exception) {
            state.setId = UUID.randomUUID().toString();
            saveState();
            return UUID.fromString(state.setId);
        }
    }

    public static synchronized void recordParticipant(MinecraftServer server, UUID playerId, String name) {
        if (server == null || playerId == null
                || MapSetProgressionPolicy.isComplete(state.completedGames, GAMES_PER_MAP)) return;
        initStorage(server);
        state.participants.put(playerId.toString(), name == null ? "" : name);
        saveState();
    }

    public static synchronized void recoverLegacyParticipants(MinecraftServer server, Map<UUID, String> participants) {
        if (server == null || participants == null || participants.isEmpty() || !state.participants.isEmpty()) return;
        initStorage(server);
        for (Map.Entry<UUID, String> entry : participants.entrySet()) {
            if (entry.getKey() != null) {
                state.participants.put(entry.getKey().toString(), entry.getValue() == null ? "" : entry.getValue());
            }
        }
        saveState();
    }

    public static synchronized Set<UUID> getParticipants() {
        Set<UUID> result = new LinkedHashSet<>();
        for (String value : state.participants.keySet()) {
            try { result.add(UUID.fromString(value)); } catch (IllegalArgumentException ignored) { }
        }
        return Set.copyOf(result);
    }

    public static synchronized Map<UUID, String> getParticipantNames() {
        Map<UUID, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : state.participants.entrySet()) {
            try {
                result.put(UUID.fromString(entry.getKey()), entry.getValue() == null ? "" : entry.getValue());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Map.copyOf(result);
    }

    public static synchronized SetRewardSummary getRewardSummary() {
        return state.rewardSummary;
    }

    public static synchronized SetLeaderboardSnapshot getLeaderboardSnapshot() {
        return state.leaderboardSnapshot;
    }

    public static synchronized boolean saveRewardSummary(MinecraftServer server, SetRewardSummary summary) {
        initStorage(server);
        state.rewardSummary = summary;
        if (saveState()) return true;
        state.rewardSummary = null;
        return false;
    }

    public static synchronized boolean saveCompletedSetResults(
            MinecraftServer server, SetLeaderboardSnapshot snapshot, SetRewardSummary summary) {
        initStorage(server);
        SetLeaderboardSnapshot previousSnapshot = state.leaderboardSnapshot;
        SetRewardSummary previousSummary = state.rewardSummary;
        state.leaderboardSnapshot = snapshot;
        state.rewardSummary = summary;
        if (saveState()) return true;
        state.leaderboardSnapshot = previousSnapshot;
        state.rewardSummary = previousSummary;
        return false;
    }

    public static synchronized void resetIncompatibleActiveSet(MinecraftServer server) {
        initStorage(server);
        TacticalTabletMod.LOGGER.warn("Resetting only the active map set because its statistics cannot be migrated safely");
        state.completedGames = 0;
        state.setId = UUID.randomUUID().toString();
        state.participants.clear();
        state.rewardSummary = null;
        state.leaderboardSnapshot = null;
        state.rewardEndsAtMillis = 0L;
        state.rewardPhaseStatus = RewardPhaseStatus.PENDING;
        state.setReportDispatched = false;
        saveState();
    }

    public static synchronized int beginOrResumeRewarding(MinecraftServer server) {
        initStorage(server);
        if (state.rewardPhaseStatus == RewardPhaseStatus.COMPLETED
                || state.rewardPhaseStatus == RewardPhaseStatus.SKIPPED) return 0;
        long now = System.currentTimeMillis();
        if (state.rewardPhaseStatus != RewardPhaseStatus.ACTIVE || state.rewardEndsAtMillis <= 0L) {
            state.rewardPhaseStatus = RewardPhaseStatus.ACTIVE;
            state.rewardEndsAtMillis = now + SET_REWARD_SECONDS * 1000L;
            saveState();
        }
        return (int) Math.max(0L, (state.rewardEndsAtMillis - now + 999L) / 1000L);
    }

    public static synchronized void completeRewarding(MinecraftServer server) {
        initStorage(server);
        state.rewardPhaseStatus = RewardPhaseStatus.COMPLETED;
        state.rewardEndsAtMillis = -1L;
        saveState();
    }

    public static synchronized boolean wasRewardingCompleted() {
        return state.rewardPhaseStatus == RewardPhaseStatus.COMPLETED
                || state.rewardPhaseStatus == RewardPhaseStatus.SKIPPED;
    }

    public static synchronized int getRewardSecondsRemaining() {
        if (state.rewardPhaseStatus != RewardPhaseStatus.ACTIVE) return 0;
        return (int) Math.max(0L, (state.rewardEndsAtMillis - System.currentTimeMillis() + 999L) / 1000L);
    }

    public static synchronized void skipRewardingForDebug(MinecraftServer server) {
        initStorage(server);
        state.completedGames = GAMES_PER_MAP;
        state.rewardSummary = null;
        state.leaderboardSnapshot = null;
        state.rewardEndsAtMillis = -1L;
        state.rewardPhaseStatus = RewardPhaseStatus.SKIPPED;
        saveState();
    }

    public static synchronized boolean isSetReportDispatched() {
        return state.setReportDispatched;
    }

    public static synchronized boolean markSetReportDispatched(
            MinecraftServer server,
            UUID expectedSetId
    ) {
        initStorage(server);
        if (expectedSetId == null || !expectedSetId.toString().equals(state.setId)) return false;
        boolean previous = state.setReportDispatched;
        return SetReportDispatchFlag.persistTrue(
                previous,
                value -> state.setReportDispatched = value,
                MapSetManager::saveState
        );
    }

    public static synchronized boolean isCompetitiveSet() {
        return state.competitiveSet;
    }

    public static synchronized boolean isClanWarSet() {
        return state.clanWarSet;
    }

    public static synchronized boolean isNextSetCompetitive() {
        return state.nextSetCompetitive;
    }

    public static synchronized boolean isNextSetClanWar() {
        return state.nextSetClanWar;
    }

    public static synchronized void announceGameStart(MinecraftServer server) {
        if (server == null) return;

        int game = getCurrentGameNumber();
        Component title = Component.literal(state.competitiveSet
                ? "Соревновательная игра " + game + " из " + GAMES_PER_MAP
                : game + "-я игра началась");
        Component subtitle = Component.literal(state.competitiveSet
                ? "Единый уровень классов: " + CompetitiveClassTierPolicy.tierForGame(game).displayName()
                : "Игра " + game + " из " + GAMES_PER_MAP);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    public static synchronized void startVoting(MinecraftServer server, boolean debug) {
        if (server == null || voting || restartSecondsLeft >= 0) return;

        initStorage(server);
        List<String> fullPool = MapVoteCandidatePolicy.normalizePool(MapRotationManager.listMapNames(server));
        if (fullPool.isEmpty()) {
            TacticalTabletMod.LOGGER.error("Cannot start map voting: map rotation pool is empty.");
            broadcast(server, "[WAR] Map voting cannot start because the map rotation pool is empty.");
            return;
        }
        int previousCompletedGames = state.completedGames;
        SetRewardSummary previousRewardSummary = state.rewardSummary;
        SetLeaderboardSnapshot previousLeaderboardSnapshot = state.leaderboardSnapshot;
        long previousRewardEndsAtMillis = state.rewardEndsAtMillis;
        RewardPhaseStatus previousRewardPhaseStatus = state.rewardPhaseStatus;
        boolean previousNextSetCompetitive = state.nextSetCompetitive;
        boolean previousNextSetClanWar = state.nextSetClanWar;
        List<String> previousHistory = List.copyOf(state.recentPlayedMaps);
        List<String> updatedHistory = MapVoteCandidatePolicy.reconcileRecentPlayedMaps(
                fullPool, previousHistory, RECENT_MAP_COOLDOWN);
        if (!debug && isSetComplete()) {
            updatedHistory = MapVoteCandidatePolicy.recordPlayedMap(
                    fullPool, updatedHistory, currentMapName(server), RECENT_MAP_COOLDOWN);
        }
        if (debug) {
            state.completedGames = GAMES_PER_MAP;
            state.rewardSummary = null;
            state.leaderboardSnapshot = null;
            state.rewardEndsAtMillis = -1L;
            state.rewardPhaseStatus = RewardPhaseStatus.SKIPPED;
        }
        state.recentPlayedMaps = new ArrayList<>(updatedHistory);

        List<String> selectedCandidates = MapVoteCandidatePolicy.selectCandidates(
                fullPool,
                state.recentPlayedMaps,
                CANDIDATE_COUNT,
                RECENT_MAP_COOLDOWN,
                RANDOM
        );
        if (selectedCandidates.size() < CANDIDATE_COUNT) {
            TacticalTabletMod.LOGGER.warn(
                    "Map voting pool contains only {} unique map(s); showing every available map.",
                    selectedCandidates.size()
            );
        }

        state.nextSetCompetitive = false;
        state.nextSetClanWar = false;
        if (!MapVoteOpeningTransaction.commit(MapSetManager::saveState, () -> {
            state.completedGames = previousCompletedGames;
            state.rewardSummary = previousRewardSummary;
            state.leaderboardSnapshot = previousLeaderboardSnapshot;
            state.rewardEndsAtMillis = previousRewardEndsAtMillis;
            state.rewardPhaseStatus = previousRewardPhaseStatus;
            state.nextSetCompetitive = previousNextSetCompetitive;
            state.nextSetClanWar = previousNextSetClanWar;
            state.recentPlayedMaps = new ArrayList<>(previousHistory);
        })) {
            TacticalTabletMod.LOGGER.error(
                    "Cannot start map voting because its required state could not be persisted.");
            broadcast(server, "[WAR] Map voting state could not be saved. Please retry.");
            return;
        }

        votingMaps = selectedCandidates;
        votes.clear();
        selectedMap = "";
        voteSecondsLeft = MAP_VOTE_SECONDS;
        voting = true;
        stopIssued = false;

        broadcast(server, debug
                ? "[WAR] Отладочное голосование за следующую карту началось. После выбора сервер будет перезапущен."
                : "[WAR] Сет из " + GAMES_PER_MAP
                + " игр завершён. Выберите следующую карту — осталось 30 секунд.");
        syncAll(server, true);
    }

    public static synchronized void vote(ServerPlayer player, String mapName) {
        if (player == null || !voting || !GameStateManager.isInLobby(player)) return;

        String canonical = canonicalMapName(mapName);
        if (canonical == null) return;

        votes.put(player.getUUID(), canonical);
        syncAll(player.server, false);
    }

    public static synchronized void setNextSetCompetitive(ServerPlayer player, boolean competitive) {
        if (player == null || !player.hasPermissions(2) || !voting) return;
        state.nextSetCompetitive = competitive;
        if (competitive) {
            state.nextSetClanWar = false;
        }
        saveState();
        broadcast(player.server, "[WAR] Следующий сет: "
                + (competitive ? "соревновательный" : "обычный casual") + ".");
        syncAll(player.server, false);
    }

    public static synchronized void setNextSetClanWar(ServerPlayer player, boolean clanWar) {
        if (player == null || !player.hasPermissions(2) || !voting) return;
        state.nextSetClanWar = clanWar;
        if (clanWar) {
            state.nextSetCompetitive = false;
        }
        saveState();
        broadcast(player.server, "[WAR] Следующий сет: "
                + (clanWar ? "война кланов" : "обычный casual") + ".");
        syncAll(player.server, false);
    }

    public static synchronized void setDebugClanWarSet(MinecraftServer server, boolean clanWar) {
        initStorage(server);
        state.clanWarSet = clanWar;
        if (clanWar) {
            state.competitiveSet = false;
            state.nextSetCompetitive = false;
        }
        saveState();
    }

    public static synchronized VoteTickResult tickVoting(MinecraftServer server) {
        if (!voting || server == null) return VoteTickResult.FAILED;

        if (voteSecondsLeft > 0) {
            voteSecondsLeft--;
            ClassXPManager.syncAll(server);
            syncAll(server, false);
            if (voteSecondsLeft > 0) return VoteTickResult.ACTIVE;
        }

        String winner = selectedMap.isBlank() ? resolveWinner() : selectedMap;
        selectedMap = winner;
        try {
            MapRotationManager.setNextMap(server, winner);
            MapRotationManager.arm(server);
        } catch (IOException | RuntimeException exception) {
            TacticalTabletMod.LOGGER.error("Failed to prepare voted map {}", winner, exception);
            voteSecondsLeft = 10;
            broadcast(server, "[WAR] Не удалось подготовить карту «" + winner + "»: " + exception.getMessage()
                    + ". Повторная попытка через 10 секунд.");
            syncAll(server, false);
            return VoteTickResult.FAILED;
        }

        voting = false;
        restartSecondsLeft = RESTART_COUNTDOWN_SECONDS;
        broadcast(server, "[WAR] Выбрана карта «" + winner + "». Перезапуск сервера через "
                + RESTART_COUNTDOWN_SECONDS + " секунд.");
        syncAll(server, false);
        return VoteTickResult.PREPARED;
    }

    public static synchronized void tickRestart(MinecraftServer server) {
        if (server == null || restartSecondsLeft < 0 || stopIssued) return;

        if (restartSecondsLeft == 0) {
            stopIssued = true;
            PlayerProgressManager.saveAll();
            broadcast(server, "[WAR] Сервер перезапускается для смены карты на «" + selectedMap + "».");
            server.halt(false);
            return;
        }

        if (restartSecondsLeft <= 5 || restartSecondsLeft == RESTART_COUNTDOWN_SECONDS) {
            broadcast(server, "[WAR] Перезапуск через " + restartSecondsLeft + " сек.");
        }
        restartSecondsLeft--;
    }

    public static synchronized void sync(ServerPlayer player, boolean openScreen) {
        if (player == null) return;
        PacketHandler.sendToPlayer(player, createStatePacket(player, openScreen));
    }

    public static synchronized boolean isVoting() {
        return voting;
    }

    public static synchronized int getVoteSecondsLeft() {
        return Math.max(0, voteSecondsLeft);
    }

    public static synchronized List<String> mapPool() {
        return votingMaps;
    }

    private static MapVoteStatePacket createStatePacket(ServerPlayer player, boolean openScreen) {
        Map<String, Integer> counts = voteCounts();
        return new MapVoteStatePacket(
                voting,
                openScreen,
                player.hasPermissions(2),
                state.nextSetCompetitive,
                state.nextSetClanWar,
                voteSecondsLeft,
                votes.getOrDefault(player.getUUID(), ""),
                votingMaps,
                counts
        );
    }

    private static void syncAll(MinecraftServer server, boolean openScreen) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player, openScreen);
        }
    }

    private static Map<String, Integer> voteCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String map : votingMaps) counts.put(map, 0);
        for (String vote : votes.values()) counts.computeIfPresent(vote, (ignored, value) -> value + 1);
        return counts;
    }

    private static String resolveWinner() {
        return MapVoteWinnerPolicy.selectWinner(votingMaps, voteCounts(), RANDOM);
    }

    private static String canonicalMapName(String value) {
        return MapVoteCandidatePolicy.canonicalMapName(votingMaps, value);
    }

    private static void initStorage(MinecraftServer server) {
        if (statePath != null || server == null) return;

        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path serverRoot = worldRoot.getParent() == null ? worldRoot : worldRoot.getParent();
        Path dataRoot = serverRoot.resolve("tacticaltablet_data");
        statePath = dataRoot.resolve(STATE_FILE);

        try {
            Files.createDirectories(dataRoot);
            if (Files.exists(statePath)) {
                try (Reader reader = Files.newBufferedReader(statePath, StandardCharsets.UTF_8)) {
                    SetState loaded = GSON.fromJson(reader, SetState.class);
                    if (loaded != null) state = loaded;
                }
            }
            if (state.dataVersion < DATA_VERSION && !backupStateBeforeMigration(state.dataVersion)) {
                normalizeState();
                statePath = null;
                return;
            }
            normalizeState();
            saveState();
        } catch (IOException | RuntimeException exception) {
            TacticalTabletMod.LOGGER.error("Failed to initialize map set state at {}", statePath, exception);
            state = new SetState();
        }
    }

    private static boolean saveState() {
        if (statePath == null) return false;
        normalizeState();
        Path temp = statePath.resolveSibling(statePath.getFileName() + ".tmp");

        try {
            Files.createDirectories(statePath.getParent());
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(state, writer);
            }
            try {
                Files.move(temp, statePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, statePath, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to save map set state at {}", statePath, exception);
            return false;
        }
    }

    private static boolean backupStateBeforeMigration(int sourceDataVersion) {
        if (statePath == null || !Files.exists(statePath)) return true;

        int safeSourceVersion = Math.max(0, sourceDataVersion);
        Path backup = statePath.resolveSibling(
                statePath.getFileName() + ".v" + safeSourceVersion + ".pre-v" + DATA_VERSION + ".bak");
        if (Files.exists(backup)) return true;

        try {
            Files.copy(statePath, backup);
            return true;
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error(
                    "Failed to back up map set state before migrating data version {} to {}",
                    safeSourceVersion,
                    DATA_VERSION,
                    exception
            );
            return false;
        }
    }

    private static void normalizeState() {
        normalizeState(state);
    }

    static void normalizeState(SetState candidate) {
        candidate.mapName = candidate.mapName == null ? "" : candidate.mapName.trim();
        candidate.lastRotation = candidate.lastRotation == null ? "" : candidate.lastRotation.trim();
        candidate.recentPlayedMaps = new ArrayList<>(MapVoteCandidatePolicy.normalizeStoredHistory(
                candidate.recentPlayedMaps, RECENT_MAP_COOLDOWN));
        MapSetProgressionPolicy.Migration migration = MapSetProgressionPolicy.migrate(
                candidate.dataVersion,
                candidate.completedGames,
                GAMES_PER_MAP
        );
        candidate.completedGames = migration.completedGames();
        candidate.dataVersion = migration.dataVersion();
        if (candidate.setId == null || candidate.setId.isBlank()) {
            candidate.setId = UUID.randomUUID().toString();
        }
        if (candidate.participants == null) candidate.participants = new LinkedHashMap<>();
        if (candidate.rewardPhaseStatus == null
                || (candidate.rewardPhaseStatus == RewardPhaseStatus.PENDING
                && candidate.rewardEndsAtMillis != 0L)) {
            candidate.rewardPhaseStatus = candidate.rewardEndsAtMillis < 0L
                    ? RewardPhaseStatus.COMPLETED
                    : candidate.rewardEndsAtMillis > 0L
                    ? RewardPhaseStatus.ACTIVE
                    : RewardPhaseStatus.PENDING;
        }
        if (candidate.competitiveSet && candidate.clanWarSet) {
            candidate.clanWarSet = false;
        }
        if (candidate.nextSetCompetitive && candidate.nextSetClanWar) {
            candidate.nextSetClanWar = false;
        }
    }

    private static String currentMapName(MinecraftServer server) {
        try {
            MapRotationManager.RotationStatus status = MapRotationManager.getStatus(server);
            if (status.currentMap() != null && !status.currentMap().isBlank()) return status.currentMap();
        } catch (RuntimeException exception) {
            TacticalTabletMod.LOGGER.warn("Could not read current map from rotation state", exception);
        }
        return server.getWorldPath(LevelResource.ROOT).getFileName().toString();
    }

    private static void reconcileRecentPlayedMaps(MinecraftServer server) {
        List<String> previousHistory = List.copyOf(state.recentPlayedMaps);
        List<String> fullPool = MapVoteCandidatePolicy.normalizePool(
                MapRotationManager.listMapNames(server));
        if (fullPool.isEmpty()) {
            TacticalTabletMod.LOGGER.warn(
                    "Skipping played map history reconciliation because the map rotation pool is empty; "
                            + "the saved cooldown history is preserved.");
            return;
        }
        List<String> normalizedHistory = MapVoteCandidatePolicy.reconcileRecentPlayedMaps(
                fullPool,
                previousHistory,
                RECENT_MAP_COOLDOWN
        );
        if (normalizedHistory.equals(previousHistory)) return;

        state.recentPlayedMaps = new ArrayList<>(normalizedHistory);
        if (!saveState()) {
            state.recentPlayedMaps = new ArrayList<>(previousHistory);
            TacticalTabletMod.LOGGER.error(
                    "Failed to reconcile played map history with the current rotation pool.");
        }
    }

    private static void resetRuntimeState() {
        votes.clear();
        voting = false;
        voteSecondsLeft = 0;
        restartSecondsLeft = -1;
        selectedMap = "";
        votingMaps = List.of();
        stopIssued = false;
    }

    private static void broadcast(MinecraftServer server, String message) {
        if (server == null) return;
        Component component = Component.literal(message);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) player.sendSystemMessage(component);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public enum VoteTickResult {
        ACTIVE,
        PREPARED,
        FAILED
    }

    static final class SetState {
        int dataVersion = DATA_VERSION;
        String mapName = "";
        String lastRotation = "";
        List<String> recentPlayedMaps = new ArrayList<>();
        int completedGames;
        boolean competitiveSet;
        boolean nextSetCompetitive;
        boolean clanWarSet;
        boolean nextSetClanWar;
        String setId = UUID.randomUUID().toString();
        Map<String, String> participants = new LinkedHashMap<>();
        SetRewardSummary rewardSummary;
        SetLeaderboardSnapshot leaderboardSnapshot;
        long rewardEndsAtMillis;
        RewardPhaseStatus rewardPhaseStatus = RewardPhaseStatus.PENDING;
        boolean setReportDispatched;
    }

    enum RewardPhaseStatus {
        PENDING,
        ACTIVE,
        COMPLETED,
        SKIPPED
    }
}
