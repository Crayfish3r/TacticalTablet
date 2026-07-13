package com.makar.tacticaltablet.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.map.MapRotationManager;
import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class MapSetManager {

    public static final int GAMES_PER_MAP = 4;
    public static final int MAP_VOTE_SECONDS = 30;
    public static final int RESTART_COUNTDOWN_SECONDS = 10;

    private static final int DATA_VERSION = 2;
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
            state.competitiveSet = state.nextSetCompetitive;
            state.clanWarSet = state.nextSetClanWar;
            state.nextSetCompetitive = false;
            state.nextSetClanWar = false;
            saveState();
        } else if (state.lastRotation.isBlank() && !lastRotation.isBlank()) {
            state.lastRotation = lastRotation;
            saveState();
        }
    }

    public static synchronized void onServerStopped() {
        saveState();
        resetRuntimeState();
        statePath = null;
        state = new SetState();
    }

    public static synchronized int getCurrentGameNumber() {
        return Math.min(GAMES_PER_MAP, Math.max(1, state.completedGames + 1));
    }

    public static synchronized int getCompletedGames() {
        return Math.max(0, Math.min(GAMES_PER_MAP, state.completedGames));
    }

    public static synchronized boolean onGameCompleted(MinecraftServer server) {
        initStorage(server);
        state.completedGames = Math.min(GAMES_PER_MAP, state.completedGames + 1);
        saveState();
        return state.completedGames >= GAMES_PER_MAP;
    }

    public static synchronized boolean isSetComplete() {
        return state.completedGames >= GAMES_PER_MAP;
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
        Component title = Component.literal(game + "-я игра началась");
        Component subtitle = Component.literal(state.competitiveSet
                ? "Соревновательный сет • игра " + game + " из " + GAMES_PER_MAP
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
        votingMaps = List.copyOf(MapRotationManager.listMapNames(server));
        if (votingMaps.isEmpty()) {
            TacticalTabletMod.LOGGER.error("Cannot start map voting: map rotation pool is empty.");
            broadcast(server, "[WAR] Map voting cannot start because the map rotation pool is empty.");
            return;
        }
        if (debug) {
            state.completedGames = GAMES_PER_MAP;
            saveState();
        }

        votes.clear();
        selectedMap = "";
        voteSecondsLeft = MAP_VOTE_SECONDS;
        voting = true;
        stopIssued = false;
        state.nextSetCompetitive = false;
        state.nextSetClanWar = false;
        saveState();

        broadcast(server, debug
                ? "[WAR] Отладочное голосование за следующую карту началось. После выбора сервер будет перезапущен."
                : "[WAR] Сет из 4 игр завершён. Выберите следующую карту — осталось 30 секунд.");
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

        String winner = resolveWinner();
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

        selectedMap = winner;
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
        Map<String, Integer> counts = voteCounts();
        int best = counts.values().stream().max(Comparator.naturalOrder()).orElse(0);
        List<String> leaders = new ArrayList<>();

        for (String map : votingMaps) {
            if (counts.getOrDefault(map, 0) == best) leaders.add(map);
        }

        if (leaders.isEmpty()) return votingMaps.isEmpty() ? "" : votingMaps.get(0);
        return leaders.get(RANDOM.nextInt(leaders.size()));
    }

    private static String canonicalMapName(String value) {
        String normalized = normalize(value);
        for (String map : votingMaps) {
            if (normalize(map).equals(normalized)) return map;
        }
        return null;
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
            normalizeState();
            saveState();
        } catch (IOException | RuntimeException exception) {
            TacticalTabletMod.LOGGER.error("Failed to initialize map set state at {}", statePath, exception);
            state = new SetState();
        }
    }

    private static void saveState() {
        if (statePath == null) return;
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
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to save map set state at {}", statePath, exception);
        }
    }

    private static void normalizeState() {
        state.dataVersion = DATA_VERSION;
        state.mapName = state.mapName == null ? "" : state.mapName.trim();
        state.lastRotation = state.lastRotation == null ? "" : state.lastRotation.trim();
        state.completedGames = Math.max(0, Math.min(GAMES_PER_MAP, state.completedGames));
        if (state.competitiveSet && state.clanWarSet) {
            state.clanWarSet = false;
        }
        if (state.nextSetCompetitive && state.nextSetClanWar) {
            state.nextSetClanWar = false;
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

    private static final class SetState {
        int dataVersion = DATA_VERSION;
        String mapName = "";
        String lastRotation = "";
        int completedGames;
        boolean competitiveSet;
        boolean nextSetCompetitive;
        boolean clanWarSet;
        boolean nextSetClanWar;
    }
}
