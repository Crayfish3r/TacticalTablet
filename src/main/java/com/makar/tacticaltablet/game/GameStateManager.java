package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.admin.TestModeManager;
import com.makar.tacticaltablet.airdrop.AirdropManager;
import com.makar.tacticaltablet.clan.ClanManager;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.clanwar.ClanWarManager;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.game.lobby.LobbyManager;
import com.makar.tacticaltablet.game.contract.ContractManager;
import com.makar.tacticaltablet.game.extraction.ExtractionPointManager;
import com.makar.tacticaltablet.game.lifecycle.LegacyMatchStateMapper;
import com.makar.tacticaltablet.game.lifecycle.MatchLifecycleService;
import com.makar.tacticaltablet.game.lifecycle.MatchLifecycleSnapshot;
import com.makar.tacticaltablet.game.lifecycle.MatchTransitionResult;
import com.makar.tacticaltablet.game.lifecycle.MatchTransitionStatus;
import com.makar.tacticaltablet.game.lifecycle.MatchStartRequest;
import com.makar.tacticaltablet.game.lifecycle.MatchStartStep;
import com.makar.tacticaltablet.game.lifecycle.MatchState;
import com.makar.tacticaltablet.game.lifecycle.integration.MatchStartCoordinator;
import com.makar.tacticaltablet.game.lifecycle.integration.MatchStartGateway;
import com.makar.tacticaltablet.game.lifecycle.integration.MatchStartPreflightResult;
import com.makar.tacticaltablet.game.lifecycle.integration.MatchStartRejectionReason;
import com.makar.tacticaltablet.game.lifecycle.integration.MatchStartResult;
import com.makar.tacticaltablet.game.lifecycle.integration.MatchStartStatus;
import com.makar.tacticaltablet.game.respawn.RespawnControlManager;
import com.makar.tacticaltablet.game.respawn.RtpTimerManager;
import com.makar.tacticaltablet.game.teleport.SafeTeleport;
import com.makar.tacticaltablet.game.team.TeamId;
import com.makar.tacticaltablet.game.team.TeamMatchManager;
import com.makar.tacticaltablet.game.team.VoteManager;
import com.makar.tacticaltablet.game.zone.ZoneManager;
import com.makar.tacticaltablet.game.set.SetRewardPresentation;
import com.makar.tacticaltablet.game.set.SetRewardCountdown;
import com.makar.tacticaltablet.game.set.SetRewardService;
import com.makar.tacticaltablet.game.set.SetRewardSummary;
import com.makar.tacticaltablet.game.set.SetMatchRuntime;
import com.makar.tacticaltablet.integration.discord.DiscordLeaderboardService;
import com.makar.tacticaltablet.inventory.InventoryManager;
import com.makar.tacticaltablet.map.WorldCleanupManager;
import com.makar.tacticaltablet.progression.ClassCooldownManager;
import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.progression.PassiveClassXPManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.voice.VoiceChatTeamManager;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class GameStateManager {

    public static final ResourceKey<Level> LOBBY_DIMENSION = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation("lobby", "lobby")
    );

    public static final int WAITING = 0;
    public static final int RUNNING = 1;

    private static final int MIN_PLAYERS = 2;
    private static final int START_DELAY_SECONDS = 10;
    private static final int POST_GAME_DELAY_SECONDS = 3;
    private static final int WIN_XP_ALL_CLASSES = 10;
    private static final String GAME_STATE_OBJECTIVE = "gameState";
    private static final ResourceLocation START_GAME_FUNCTION = new ResourceLocation("war", "start_game");
    private static final ResourceLocation RESET_GAME_FUNCTION = new ResourceLocation("war", "reset");

    private static boolean matchHadEnoughPlayers = false;
    private static int matchStartingParticipants = 0;
    private static int tickCounter = 0;
    private static int startCountdown = -1;
    private static int postGameDelay = 0;
    private static final SetRewardCountdown SET_REWARD_COUNTDOWN = new SetRewardCountdown();
    private static MatchPhase matchPhase = MatchPhase.WAITING;
    private static MatchMode currentMode = MatchMode.SOLO;
    private static boolean startTransitionPlayerSetup = false;
    private static final MatchLifecycleService MATCH_LIFECYCLE = new MatchLifecycleService();
    private static final MatchStartCoordinator MATCH_START_COORDINATOR =
            new MatchStartCoordinator(MATCH_LIFECYCLE, new GameStateMatchStartGateway());

    public static int getGameState(MinecraftServer server) {
        if (server == null) return WAITING;

        Objective objective = getOrCreateGameStateObjective(server);
        if (objective == null) return WAITING;

        return server.getScoreboard().getOrCreatePlayerScore("#state", objective).getScore();
    }

    public static void setGameState(MinecraftServer server, int state) {
        if (server == null) return;

        Objective objective = getOrCreateGameStateObjective(server);
        if (objective == null) return;

        Scoreboard scoreboard = server.getScoreboard();
        int current = scoreboard.getOrCreatePlayerScore("#state", objective).getScore();
        if (current != state) {
            scoreboard.getOrCreatePlayerScore("#state", objective).setScore(state);
        }
    }

    private static Objective getOrCreateGameStateObjective(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(GAME_STATE_OBJECTIVE);
        if (objective != null) return objective;

        CommandSourceStack source = server.createCommandSourceStack()
                .withSuppressedOutput()
                .withPermission(4);
        server.getCommands().performPrefixedCommand(
                source,
                "scoreboard objectives add " + GAME_STATE_OBJECTIVE + " dummy"
        );

        return scoreboard.getObjective(GAME_STATE_OBJECTIVE);
    }

    public static boolean isRunning(MinecraftServer server) {
        return getGameState(server) == RUNNING;
    }

    public static MatchLifecycleSnapshot getLifecycleSnapshot() {
        return MATCH_START_COORDINATOR.snapshot();
    }

    static boolean registerCurrentMatchParticipant(UUID matchId, UUID playerId) {
        if (matchId == null || playerId == null) return false;
        MatchTransitionResult result = MATCH_START_COORDINATOR.registerParticipant(matchId, playerId);
        return (result.status() == MatchTransitionStatus.APPLIED
                || result.status() == MatchTransitionStatus.NO_OP)
                && MATCH_START_COORDINATOR.snapshot().participantIds().contains(playerId);
    }

    public static boolean isStartTransitionPlayerSetup() {
        return startTransitionPlayerSetup;
    }

    public static MatchPhase getMatchPhase() {
        return matchPhase;
    }

    static MatchState lifecycleStateForLegacyState(int gameState, MatchPhase phase) {
        return LegacyMatchStateMapper.fromLegacyState(RUNNING, gameState, phase);
    }

    public static MatchMode getCurrentMode() {
        return currentMode;
    }

    public static int getLivesPerPlayer() {
        if (MapSetManager.isClanWarSet()) return 1;
        return currentMode.livesPerPlayer();
    }

    public static boolean isTabletAvailableInLobby(MinecraftServer server) {
        if (server == null) return false;
        return TabletLobbyPolicy.isTabletAvailable(
                isRunning(server),
                startTransitionPlayerSetup,
                MapSetManager.isClanWarSet(),
                matchPhase
        );
    }

    public static boolean isInLobby(ServerPlayer player) {
        return player != null && player.level().dimension().equals(LOBBY_DIMENSION);
    }

    public static ServerLevel getLobbyLevel(MinecraftServer server) {
        return server == null ? null : server.getLevel(LOBBY_DIMENSION);
    }

    public static ServerLevel getOverworld(MinecraftServer server) {
        return server == null ? null : server.getLevel(Level.OVERWORLD);
    }

    public static int onlinePlayers(MinecraftServer server) {
        return server == null ? 0 : server.getPlayerList().getPlayerCount();
    }

    public static int playingPlayers(MinecraftServer server) {
        return LivesManager.getAlivePlayerCount(server);
    }

    private static ServerPlayer findWinner(MinecraftServer server) {
        if (server == null) return null;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (LivesManager.isAliveParticipant(player)) {
                return player;
            }
        }

        return null;
    }

    public static void onServerTick(MinecraftServer server) {
        if (server == null) return;
        if (++tickCounter < 20) return;
        tickCounter = 0;

        if (postGameDelay > 0) {
            postGameDelay--;
            if (postGameDelay <= 0) {
                cleanupMatchRuntime(server);
                if (MapSetManager.isSetComplete()) {
                    beginSetRewarding(server);
                } else {
                    matchPhase = MatchPhase.WAITING;
                }
                ClassXPManager.syncAll(server);
            }
            return;
        }

        if (!isRunning(server)) {
            handleWaitingTick(server);
            return;
        }

        startCountdown = -1;
        matchPhase = MatchPhase.RUNNING;
        ZoneManager.tick(server);
        checkForMatchEnd(server);
    }

    public static void checkForMatchEnd(MinecraftServer server) {
        if (server == null || !isRunning(server) || postGameDelay > 0) return;

        if (MapSetManager.isClanWarSet()) {
            int aliveUnits = ClanWarManager.getAliveUnitCount(server);
            if (aliveUnits <= 1 && (!ClanWarManager.isSoloDebugEnabled() || aliveUnits <= 0)) {
                ServerPlayer winner = ClanWarManager.findWinningUnitRepresentative(server);
                TeamId winnerTeam = TeamMatchManager.getTeam(winner);
                endGame(server, winner, clanWarWinnerLabel(winner), winnerTeam);
            }
            return;
        }

        if (currentMode.isTeamMode()) {
            int aliveTeams = TeamMatchManager.getAliveTeamCount(server);
            if (aliveTeams <= 1) {
                TeamId winningTeam = TeamMatchManager.findWinningTeam(server);
                List<ServerPlayer> winners = TeamMatchManager.findWinningPlayers(server);
                ServerPlayer winner = winners.isEmpty() ? null : winners.get(0);
                String winnerLabel = winningTeam == null ? "Нет победителя" : winningTeam.displayName();
                endGame(server, winners, winner, winnerLabel, winningTeam);
            }
            return;
        }

        int alive = playingPlayers(server);
        if (matchStartingParticipants > 0 && alive <= 0) {
            endGame(server, null, "Нет победителя");
            return;
        }

        int requiredPlayers = TestModeManager.getRequiredPlayers(MIN_PLAYERS);

        if (requiredPlayers <= 1) {
            return;
        }

        if (alive >= requiredPlayers) {
            matchHadEnoughPlayers = true;
            return;
        }

        if (matchHadEnoughPlayers && alive <= 1) {
            endGame(server, findWinner(server));
        }
    }

    public static void startGame(MinecraftServer server) {
        if (server == null) return;
        handleStartResult(server, MATCH_START_COORDINATOR.start(server));
    }

    private static void handleStartResult(MinecraftServer server, MatchStartResult result) {
        switch (result.status()) {
            case STARTED -> {
            }
            case REJECTED -> handleRejectedStart(server, result);
            case ALREADY_STARTING -> broadcast(server, "[WAR] Match start is already in progress.");
            case ALREADY_RUNNING -> broadcast(server, "[WAR] Match is already running.");
            case BLOCKED_REQUIRES_CLEANUP -> broadcast(server,
                    "[WAR] New match start blocked: previous start attempt requires cleanup.");
            case FAILED_ROLLED_BACK -> broadcast(server,
                    "[WAR] Match start failed and was rolled back. Check the log.");
            case FAILED_REQUIRES_CLEANUP -> broadcast(server,
                    "[WAR] Match start failed. Cleanup is required; check the log.");
            case STALE_OPERATION -> TacticalTabletMod.LOGGER.warn(
                    "Stale match start operation rejected: {}", result.diagnostic());
        }
    }

    private static void handleRejectedStart(MinecraftServer server, MatchStartResult result) {
        if (result.rejectionReason() == MatchStartRejectionReason.RUNTIME_REQUIREMENTS_FAILED) {
            broadcast(server, "[WAR] Match start cancelled: server setup is incomplete. Check the log.");
            return;
        }
        if (result.rejectionReason() == MatchStartRejectionReason.INSUFFICIENT_PLAYERS) {
            broadcast(server, "[WAR] Match start cancelled: not enough players.");
            return;
        }
        TacticalTabletMod.LOGGER.warn("Match start rejected reason={} diagnostic={}",
                result.rejectionReason(), result.diagnostic());
    }

    private static String clanWarWinnerLabel(ServerPlayer winner) {
        if (winner == null) return "РќРµС‚ РїРѕР±РµРґРёС‚РµР»СЏ";
        String clanName = ClanManager.getClanNameForPlayer(winner);
        return clanName.isBlank() ? winner.getName().getString() : clanName;
    }

    public static void endGame(MinecraftServer server) {
        endGame(server, findWinner(server));
    }

    public static void endGame(MinecraftServer server, ServerPlayer winner) {
        endGame(server, winner, winner != null ? winner.getName().getString() : "Нет победителя", null);
    }

    private static void endGame(MinecraftServer server, ServerPlayer winner, String winnerName) {
        endGame(server, winner, winnerName, null);
    }

    private static void endGame(MinecraftServer server, ServerPlayer winner, String winnerName, TeamId winnerTeam) {
        List<ServerPlayer> winners = winnerTeam == null
                ? List.of()
                : TeamMatchManager.getTeamPlayers(server, winnerTeam);
        endGame(server, winners, winner, winnerName, winnerTeam);
    }

    private static void endGame(MinecraftServer server, List<ServerPlayer> winners, ServerPlayer displayWinner, String winnerName, TeamId winnerTeam) {
        if (server == null || !isRunning(server) || matchPhase == MatchPhase.POST_GAME
                || matchPhase == MatchPhase.SET_REWARDING || matchPhase == MatchPhase.MAP_VOTING
                || matchPhase == MatchPhase.RESTARTING) return;

        matchHadEnoughPlayers = false;
        matchStartingParticipants = 0;
        startCountdown = -1;
        postGameDelay = POST_GAME_DELAY_SECONDS;
        matchPhase = MatchPhase.POST_GAME;
        setGameState(server, WAITING);
        SpectatorCameraManager.onMatchEnd(server);
        VoiceChatTeamManager.endMatch(server);
        applySelectedClassCooldowns(server);

        ContractManager.finishMatch(server);
        ExtractionPointManager.reset(server);
        boolean clanWarSet = MapSetManager.isClanWarSet();
        boolean completingSet = MapSetManager.getCompletedGames() + 1 >= MapSetManager.GAMES_PER_MAP;
        List<ServerPlayer> normalizedWinners = normalizedWinners(winners, displayWinner);
        boolean hasEligibleWinner = !normalizedWinners.isEmpty();
        if (!hasEligibleWinner) {
            displayWinner = null;
            winnerName = "Нет победителя";
            winnerTeam = null;
        } else if (displayWinner == null
                || !MatchAdmissionManager.isCurrentMatchParticipant(displayWinner.getUUID())) {
            displayWinner = normalizedWinners.get(0);
            if (winnerTeam == null) {
                winnerName = displayWinner.getName().getString();
            }
        }

        for (ServerPlayer winner : normalizedWinners) {
            PlayerProgressManager.addWin(winner);
            ClassXPManager.addXPToAllClasses(winner, WIN_XP_ALL_CLASSES);
            PlayerProgressManager.savePlayer(winner);
            ClassXPManager.sync(winner);
        }

        boolean setComplete;
        SetRewardSummary setSummary;
        if (clanWarSet) {
            // Preserve the established clan-war durability boundary before its separate clan coin award.
            setComplete = MapSetManager.onGameCompleted(server);
            setSummary = DiscordLeaderboardService.sendCurrentMatchLeaderboard(
                    server, normalizedWinners, setComplete, true);
        } else {
            setSummary = DiscordLeaderboardService.sendCurrentMatchLeaderboard(
                    server, normalizedWinners, completingSet, false);
            setComplete = MapSetManager.onGameCompleted(server);
        }
        ClassXPManager.syncAll(server);

        if (setComplete && !clanWarSet && setSummary != null) {
            awardSetAndLogFailures(server, setSummary);
            dispatchSetReportOnce(server, setSummary);
        }

        showWinnerTitle(server, winnerName, winnerTeam);
    }



    private static List<ServerPlayer> normalizedWinners(List<ServerPlayer> winners, ServerPlayer fallbackWinner) {
        List<ServerPlayer> result = new ArrayList<>();
        if (winners != null) {
            for (ServerPlayer winner : winners) {
                if (winner != null
                        && MatchAdmissionManager.isCurrentMatchParticipant(winner.getUUID())
                        && !result.contains(winner)) {
                    result.add(winner);
                }
            }
        }
        if (result.isEmpty() && fallbackWinner != null
                && MatchAdmissionManager.isCurrentMatchParticipant(fallbackWinner.getUUID())) {
            result.add(fallbackWinner);
        }
        return result;
    }
    private static void applySelectedClassCooldowns(MinecraftServer server) {
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ClassCooldownManager.setCooldownForSelectedClass(player);
        }

        ClassXPManager.syncAll(server);
    }
    public static void resetRuntime(MinecraftServer server) {
        matchHadEnoughPlayers = false;
        matchStartingParticipants = 0;
        tickCounter = 0;
        startCountdown = -1;
        postGameDelay = 0;
        SET_REWARD_COUNTDOWN.reset();
        matchPhase = MatchPhase.WAITING;
        currentMode = MatchMode.SOLO;
        SetMatchRuntime.reset();
        ClanWarManager.resetRuntime();
        VoteManager.reset();
        SpectatorCameraManager.onMatchEnd(server);
        VoiceChatTeamManager.endMatch(server);
        TeamMatchManager.reset(server);
        ExtractionPointManager.reset(server);

        if (server != null) {
            setGameState(server, WAITING);
        }
        MATCH_START_COORDINATOR.clearAfterLegacyCleanup();
    }

    public static boolean forceStopMatch(MinecraftServer server) {
        if (server == null) return false;

        boolean hadActiveState = isRunning(server)
                || matchPhase != MatchPhase.WAITING
                || postGameDelay > 0
                || startCountdown >= 0;

        matchHadEnoughPlayers = false;
        matchStartingParticipants = 0;
        tickCounter = 0;
        startCountdown = -1;
        postGameDelay = 0;
        SET_REWARD_COUNTDOWN.reset();
        matchPhase = MatchPhase.WAITING;
        SetMatchRuntime.reset();

        cleanupMatchRuntime(server);
        broadcast(server, hadActiveState
                ? "[WAR] Матч принудительно остановлен."
                : "[WAR] Состояние матча сброшено.");
        ClassXPManager.syncAll(server);
        return hadActiveState;
    }

    private static void cleanupMatchRuntime(MinecraftServer server) {
        if (server == null) return;

        setGameState(server, WAITING);
        SpectatorCameraManager.onMatchEnd(server);
        VoiceChatTeamManager.endMatch(server);
        TeamMatchManager.cleanupScoreboardTeams(server);
        AirdropManager.resetAutoScheduler();
        ContractManager.reset(server);
        ExtractionPointManager.reset(server);
        ServerLevel activeAirdropLevel = getOverworld(server);
        if (activeAirdropLevel != null) {
            AirdropManager.cancel(activeAirdropLevel);
        }
        ZoneManager.reset(server);
        RespawnControlManager.reset(server);
        PassiveClassXPManager.clearAll();
        RtpTimerManager.clearAll();
        SafeTeleport.clearPool();
        ClanWarManager.resetRuntime();

        WorldCleanupManager.clearDroppedItems(server);

        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, "function " + RESET_GAME_FUNCTION);
        DropControlManager.enforceGameRules(server);

        LivesManager.resetAll(server);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.removeTag("war.playing");
            player.removeTag("in_lobby");
            LobbyManager.moveToLobby(player);
            ClassXPManager.sync(player);
        }

        currentMode = MatchMode.SOLO;
        VoteManager.reset();
        TeamMatchManager.reset(server);
        MATCH_START_COORDINATOR.clearAfterLegacyCleanup();
    }

    public static boolean validateRuntimeRequirements(MinecraftServer server) {
        if (server == null) return false;

        boolean valid = true;
        if (getOverworld(server) == null) {
            TacticalTabletMod.LOGGER.error("Tactical Tablet setup error: overworld dimension is unavailable.");
            valid = false;
        }
        if (getLobbyLevel(server) == null) {
            TacticalTabletMod.LOGGER.error("Tactical Tablet setup error: lobby:lobby dimension is unavailable.");
            valid = false;
        }
        if (!hasFunction(server, START_GAME_FUNCTION)) {
            TacticalTabletMod.LOGGER.error("Tactical Tablet setup error: datapack function {} is missing.", START_GAME_FUNCTION);
            valid = false;
        }
        if (!hasFunction(server, RESET_GAME_FUNCTION)) {
            TacticalTabletMod.LOGGER.error("Tactical Tablet setup error: datapack function {} is missing.", RESET_GAME_FUNCTION);
            valid = false;
        }

        getOrCreateGameStateObjective(server);
        return valid;
    }

    private static boolean hasFunction(MinecraftServer server, ResourceLocation id) {
        return server.getFunctions().get(id).isPresent();
    }

    private static boolean validateStartRuntimeRequirements(MinecraftServer server) {
        if (server == null) return false;

        boolean valid = true;
        if (getOverworld(server) == null) {
            TacticalTabletMod.LOGGER.error("Tactical Tablet setup error: overworld dimension is unavailable.");
            valid = false;
        }
        if (getLobbyLevel(server) == null) {
            TacticalTabletMod.LOGGER.error("Tactical Tablet setup error: lobby:lobby dimension is unavailable.");
            valid = false;
        }
        if (!hasFunction(server, START_GAME_FUNCTION)) {
            TacticalTabletMod.LOGGER.error("Tactical Tablet setup error: datapack function {} is missing.", START_GAME_FUNCTION);
            valid = false;
        }
        if (!hasFunction(server, RESET_GAME_FUNCTION)) {
            TacticalTabletMod.LOGGER.error("Tactical Tablet setup error: datapack function {} is missing.", RESET_GAME_FUNCTION);
            valid = false;
        }
        return valid;
    }

    public static boolean forceStartVoting(MinecraftServer server) {
        if (server == null || isRunning(server) || hasPendingSetReward()) return false;

        postGameDelay = 0;
        startCountdown = -1;
        currentMode = MatchMode.SOLO;
        TeamMatchManager.reset(server);
        VoteManager.start();
        matchPhase = MatchPhase.VOTING;
        setGameState(server, WAITING);
        broadcast(server, "[WAR] Отладочное голосование началось: " + describeVoteModes(server) + ".");
        giveLobbyTabletsAndSync(server);
        return true;
    }

    public static boolean forceStartMapVoting(MinecraftServer server) {
        if (server == null || isRunning(server) || hasPendingSetReward()) return false;

        postGameDelay = 0;
        startCountdown = -1;
        cleanupMatchRuntime(server);
        beginMapVoting(server, true);
        return MapSetManager.isVoting();
    }

    public static boolean forceStartTeamSelect(MinecraftServer server, MatchMode mode) {
        if (server == null || isRunning(server) || hasPendingSetReward() || mode == null || !mode.isTeamMode()) return false;

        postGameDelay = 0;
        startCountdown = -1;
        VoteManager.reset();
        currentMode = mode;
        TeamMatchManager.startSelection(mode);
        matchPhase = MatchPhase.TEAM_SELECT;
        setGameState(server, WAITING);
        broadcast(server, "[WAR] Отладочный выбор команды начался: " + mode.displayName() + ".");
        giveLobbyTabletsAndSync(server);
        return true;
    }

    public static boolean forceStartClanWar(MinecraftServer server, boolean skipPreStartWait) {
        if (server == null || isRunning(server) || hasPendingSetReward()) return false;

        postGameDelay = 0;
        startCountdown = -1;
        cleanupMatchRuntime(server);
        currentMode = MatchMode.SQUADS;
        matchPhase = MatchPhase.WAITING;

        if (skipPreStartWait) {
            ClanWarManager.skipPreStartWait();
            startGame(server);
        } else {
            setGameState(server, WAITING);
            giveLobbyTabletsAndSync(server);
        }

        return true;
    }

    private static boolean hasPendingSetReward() {
        return MapSetManager.isSetComplete() && !MapSetManager.wasRewardingCompleted();
    }

    private static void handleWaitingTick(MinecraftServer server) {
        matchHadEnoughPlayers = false;

        if (matchPhase == MatchPhase.MAP_VOTING) {
            MapSetManager.VoteTickResult result = MapSetManager.tickVoting(server);
            if (result == MapSetManager.VoteTickResult.PREPARED) {
                matchPhase = MatchPhase.RESTARTING;
                ClassXPManager.syncAll(server);
            }
            return;
        }

        if (matchPhase == MatchPhase.RESTARTING) {
            MapSetManager.tickRestart(server);
            return;
        }

        if (matchPhase == MatchPhase.SET_REWARDING) {
            if (SET_REWARD_COUNTDOWN.tickSecond()) {
                MapSetManager.completeRewarding(server);
                DiscordLeaderboardService.clearCompletedSetState();
                beginMapVoting(server, false);
            }
            return;
        }

        if (matchPhase == MatchPhase.WAITING && MapSetManager.isSetComplete()) {
            if (MapSetManager.wasRewardingCompleted()) beginMapVoting(server, false);
            else beginSetRewarding(server);
            return;
        }

        if (matchPhase == MatchPhase.VOTING) {
            handleVotingTick(server);
            return;
        }

        if (matchPhase == MatchPhase.TEAM_SELECT) {
            handleTeamSelectTick(server);
            return;
        }

        if (matchPhase == MatchPhase.STARTING) {
            handleStartingTick(server);
            return;
        }

        int requiredPlayers = TestModeManager.getRequiredPlayers(MIN_PLAYERS);

        if (onlinePlayers(server) < requiredPlayers) {
            if (MapSetManager.isClanWarSet()) {
                giveLobbyTabletsAndSync(server);
            }
            startCountdown = -1;
            return;
        }

        if (MapSetManager.isClanWarSet()) {
            giveLobbyTabletsAndSync(server);
            if (ClanWarManager.tickPreStartWait(server)) {
                return;
            }
            currentMode = MatchMode.SQUADS;
            startGame(server);
            return;
        }

        if (canStartVoting(server) && !TestModeManager.isSoloStartEnabled()) {
            matchPhase = MatchPhase.VOTING;
            VoteManager.start();
            startCountdown = -1;
            broadcast(server, "[WAR] Голосование за режим матча: " + describeVoteModes(server) + ".");
            giveLobbyTabletsAndSync(server);
            return;
        }

        if (startCountdown < 0) {
            matchPhase = MatchPhase.STARTING;
            startCountdown = START_DELAY_SECONDS;
            String suffix = TestModeManager.isSoloStartEnabled() ? " (соло-тест)" : "";
            broadcast(server, "[WAR] Матч начнётся через " + startCountdown + " сек." + suffix);
            return;
        }
    }

    private static void handleStartingTick(MinecraftServer server) {
        int requiredPlayers = TestModeManager.getRequiredPlayers(MIN_PLAYERS);

        if (onlinePlayers(server) < requiredPlayers) {
            matchPhase = MatchPhase.WAITING;
            startCountdown = -1;
            return;
        }

        if (startCountdown == 0) {
            currentMode = MapSetManager.isClanWarSet() ? MatchMode.SQUADS : MatchMode.SOLO;
            startGame(server);
            startCountdown = -1;
            return;
        }

        if (startCountdown <= 5 || startCountdown == START_DELAY_SECONDS) {
            broadcast(server, "[WAR] Матч начнётся через " + startCountdown + "...");
        }

        startCountdown--;
    }

    private static void handleVotingTick(MinecraftServer server) {
        if (!canStartVoting(server)) {
            VoteManager.reset();
            matchPhase = MatchPhase.WAITING;
            currentMode = MatchMode.SOLO;
            ClassXPManager.syncAll(server);
            return;
        }

        VoteManager.tickSecond();
        ClassXPManager.syncAll(server);

        if (!VoteManager.isComplete()) return;

        currentMode = VoteManager.resolve(server);
        broadcast(server, "[WAR] Результат голосования: " + currentMode.displayName() + ".");

        if (currentMode.isTeamMode()) {
            TeamMatchManager.startSelection(currentMode);
            matchPhase = MatchPhase.TEAM_SELECT;
            broadcast(server, "[WAR] Выбери команду в открытом окне.");
            giveLobbyTabletsAndSync(server);
            return;
        }

        startGame(server);
    }

    private static void handleTeamSelectTick(MinecraftServer server) {
        if (!currentMode.isTeamMode() || !hasEnoughPlayersForMode(server, currentMode)) {
            TeamMatchManager.reset(server);
            currentMode = MatchMode.SOLO;
            matchPhase = MatchPhase.WAITING;
            ClassXPManager.syncAll(server);
            return;
        }

        TeamMatchManager.tickSecond();
        ClassXPManager.syncAll(server);

        if (!TeamMatchManager.isSelectionComplete()) return;

        TeamMatchManager.autoBalance(server, currentMode);
        startGame(server);
    }

    private static boolean canStartVoting(MinecraftServer server) {
        return onlinePlayers(server) >= MatchMode.DUO.minPlayers()
                || TestModeManager.canBypassTeamModeMinimums();
    }

    private static boolean hasEnoughPlayersForMode(MinecraftServer server, MatchMode mode) {
        return onlinePlayers(server) >= mode.minPlayers()
                || TestModeManager.canBypassTeamModeMinimums();
    }

    private static void showWinnerTitle(MinecraftServer server, String winnerName, TeamId winnerTeam) {
        boolean noWinner = winnerName == null
                || winnerName.isBlank()
                || "No winner".equalsIgnoreCase(winnerName)
                || "Нет победителя".equalsIgnoreCase(winnerName);
        MutableComponent title = Component.literal(noWinner ? "ИГРА ЗАВЕРШЕНА"
                : winnerTeam == null ? "ПОБЕДИТЕЛЬ ИГРЫ" : "ПОБЕДИТЕЛИ ИГРЫ");
        MutableComponent subtitle = Component.literal(noWinner ? "Победитель не определён" : winnerName);
        MutableComponent chat = Component.literal(noWinner ? "[WAR] Игра завершена. Победитель не определён." : "[WAR] Победитель игры: ");

        if (!noWinner && winnerTeam != null) {
            subtitle.withStyle(winnerTeam.chatColor());
            chat.append(Component.literal(winnerName).withStyle(winnerTeam.chatColor()))
                    .append(Component.literal("."));
        } else if (!noWinner) {
            chat.append(Component.literal(winnerName + "."));
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 100, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            player.sendSystemMessage(chat);
        }
    }

    private static void broadcast(MinecraftServer server, String message) {
        Component component = Component.literal(message);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(component);
        }
    }

    private static void giveLobbyTabletsAndSync(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            LobbyManager.giveTabletIfMissing(player);
            ClassXPManager.sync(player);
        }
    }

    private static void beginMapVoting(MinecraftServer server, boolean debug) {
        matchPhase = MatchPhase.MAP_VOTING;
        currentMode = MatchMode.SOLO;
        VoteManager.reset();
        TeamMatchManager.reset(server);
        giveLobbyTabletsAndSync(server);
        MapSetManager.startVoting(server, debug);
        if (!MapSetManager.isVoting()) {
            matchPhase = MatchPhase.WAITING;
        }
    }

    private static void beginSetRewarding(MinecraftServer server) {
        if (matchPhase == MatchPhase.SET_REWARDING) return;
        if (MapSetManager.wasRewardingCompleted()) {
            beginMapVoting(server, false);
            return;
        }
        SetRewardSummary summary = MapSetManager.getRewardSummary();
        if (!MapSetManager.isClanWarSet() && summary == null) {
            summary = DiscordLeaderboardService.prepareCompletedSetSummary(server);
            if (summary == null) {
                TacticalTabletMod.LOGGER.error("Set {} is complete, but its reward summary could not be persisted; retrying",
                        MapSetManager.getSetId());
                matchPhase = MatchPhase.WAITING;
                return;
            }
        }
        matchPhase = MatchPhase.SET_REWARDING;
        currentMode = MatchMode.SOLO;
        SET_REWARD_COUNTDOWN.resume(MapSetManager.beginOrResumeRewarding(server));
        if (summary != null && !MapSetManager.isClanWarSet()) {
            List<SetRewardService.PayoutResult> payouts = awardSetAndLogFailures(server, summary);
            dispatchSetReportOnce(server, summary);
            Component title = SetRewardPresentation.title(summary);
            Component subtitle = SetRewardPresentation.subtitle(summary);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 280, 10));
                player.connection.send(new ClientboundSetTitleTextPacket(title));
                player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
                for (Component line : SetRewardPresentation.chat(
                        summary, SetRewardService.successfullyPersistedPlaces(payouts))) {
                    player.sendSystemMessage(line);
                }
            }
        }
    }

    private static List<SetRewardService.PayoutResult> awardSetAndLogFailures(
            MinecraftServer server, SetRewardSummary summary) {
        List<SetRewardService.PayoutResult> payouts = SetRewardService.award(
                server, summary, MapSetManager.isCompetitiveSet());
        for (SetRewardService.PayoutResult payout : payouts) {
            if (payout.result().status() == com.makar.tacticaltablet.clan.transaction.RepositoryResult.Status.FAILED
                    || payout.result().status() == com.makar.tacticaltablet.clan.transaction.RepositoryResult.Status.CONFLICT) {
                TacticalTabletMod.LOGGER.error("Failed set reward {} place {} ({} coins) for {}: {}", summary.setId(),
                        payout.placement().place(), payout.coins(), payout.placement().playerId(), payout.result().diagnostic());
            }
        }
        return payouts;
    }

    private static void dispatchSetReportOnce(MinecraftServer server, SetRewardSummary summary) {
        if (MapSetManager.isSetReportDispatched()) return;
        DiscordLeaderboardService.sendCompletedSetReport(server, summary);
        MapSetManager.markSetReportDispatched(server);
    }

    public static void showCurrentSetRewardOnJoin(ServerPlayer player) {
        if (player == null || matchPhase != MatchPhase.SET_REWARDING) return;
        SetRewardSummary summary = MapSetManager.getRewardSummary();
        if (summary == null || summary.placements().isEmpty()) return;
        int stayTicks = Math.max(1, MapSetManager.getRewardSecondsRemaining() * 20 - 20);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, stayTicks, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(SetRewardPresentation.title(summary)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(SetRewardPresentation.subtitle(summary)));
    }

    private static String describeVoteModes(MinecraftServer server) {
        int online = onlinePlayers(server);
        return MatchMode.selectableModes(online, TestModeManager.canBypassTeamModeMinimums())
                .stream()
                .map(MatchMode::displayName)
                .reduce((left, right) -> left + " / " + right)
                .orElse(MatchMode.SOLO.displayName());
    }

    private static final class GameStateMatchStartGateway implements MatchStartGateway {
        @Override
        public MatchStartPreflightResult preflight(MinecraftServer server, MatchLifecycleSnapshot lifecycleSnapshot) {
            if (server == null) {
                return MatchStartPreflightResult.rejected(
                        MatchStartRejectionReason.SERVER_UNAVAILABLE,
                        "server is unavailable"
                );
            }
            if (lifecycleSnapshot.state() != MatchState.IDLE) {
                return MatchStartPreflightResult.rejected(
                        MatchStartRejectionReason.LIFECYCLE_NOT_IDLE,
                        "lifecycle is " + lifecycleSnapshot.state()
                );
            }
            if (currentMode == null) {
                return MatchStartPreflightResult.rejected(
                        MatchStartRejectionReason.INVALID_MODE,
                        "current mode is null"
                );
            }
            if (onlinePlayers(server) < TestModeManager.getRequiredPlayers(MIN_PLAYERS)) {
                return MatchStartPreflightResult.rejected(
                        MatchStartRejectionReason.INSUFFICIENT_PLAYERS,
                        "not enough players"
                );
            }
            if (!validateStartRuntimeRequirements(server)) {
                return MatchStartPreflightResult.rejected(
                        MatchStartRejectionReason.RUNTIME_REQUIREMENTS_FAILED,
                        "runtime requirements failed"
                );
            }
            return MatchStartPreflightResult.acceptedResult();
        }

        @Override
        public MatchStartRequest createRequest(MinecraftServer server) {
            Set<UUID> participants = new LinkedHashSet<>();
            if (server != null) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    participants.add(player.getUUID());
                }
            }
            ResourceLocation mapId = server == null || getOverworld(server) == null
                    ? Level.OVERWORLD.location()
                    : getOverworld(server).dimension().location();
            return new MatchStartRequest(
                    mapId.toString(),
                    currentMode.name(),
                    MapSetManager.isClanWarSet() ? "clan-war" : "standard",
                    null,
                    participants,
                    Instant.now()
            );
        }

        @Override
        public void apply(MinecraftServer server, MatchStartStep step) {
            switch (step) {
                case RESET_TRANSIENT_RUNTIME -> {
                    startCountdown = -1;
                    postGameDelay = 0;
                    matchPhase = MatchPhase.STARTING;
                    RtpTimerManager.clearAll();
                    PassiveClassXPManager.clearAll();
                    RespawnControlManager.reset(server);
                    LivesManager.resetAll(server);
                }
                case CONFIGURE_TEAMS -> {
                    if (MapSetManager.isClanWarSet()) {
                        ClanWarManager.startMatch(server);
                        currentMode = MatchMode.SQUADS;
                        TeamMatchManager.assignClanWarTeams(server);
                        TeamMatchManager.applyScoreboardTeams(server);
                    } else if (currentMode.isTeamMode()) {
                        TeamMatchManager.autoBalance(server, currentMode);
                        TeamMatchManager.applyScoreboardTeams(server);
                    } else {
                        TeamMatchManager.reset(server);
                    }
                }
                case RESET_AIRDROP_SCHEDULER -> AirdropManager.resetAutoScheduler();
                case START_DISCORD_TRACKING -> {
                    SetMatchRuntime.startMatch(currentMode);
                    DiscordLeaderboardService.startMatch(server);
                }
                case START_CONTRACTS -> ContractManager.onMatchStart(server);
                case RESET_MATCH_COUNTERS -> {
                    matchHadEnoughPlayers = false;
                    matchStartingParticipants = 0;
                }
                case EXECUTE_START_DATAPACK -> {
                    CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
                    int result = server.getCommands().performPrefixedCommand(source, "function " + START_GAME_FUNCTION);
                    requireDatapackCommandSuccess(START_GAME_FUNCTION, result);
                }
                case ANNOUNCE_MAP_START -> MapSetManager.announceGameStart(server);
                case ENFORCE_GAME_RULES -> DropControlManager.enforceGameRules(server);
                case START_ZONE -> ZoneManager.start(server);
                case PREPARE_SAFE_TELEPORT -> SafeTeleport.preparePool(server);
                case INITIALIZE_PLAYERS -> initializePlayers(server);
                case START_VOICE_MATCH -> VoiceChatTeamManager.startTeamMatch(server);
                case CAPTURE_PARTICIPANTS -> {
                    captureCurrentMatchParticipants(server);
                    matchStartingParticipants = playingPlayers(server);
                    matchHadEnoughPlayers = matchStartingParticipants >= TestModeManager.getRequiredPlayers(MIN_PLAYERS);
                }
                case START_EXTRACTION -> ExtractionPointManager.onMatchStart(server);
                case SYNC_CLASS_XP -> ClassXPManager.syncAll(server);
                case SET_LEGACY_RUNNING -> {
                    setGameState(server, RUNNING);
                    if (getGameState(server) != RUNNING) {
                        throw new IllegalStateException("legacy scoreboard did not commit RUNNING state");
                    }
                    matchPhase = MatchPhase.RUNNING;
                }
            }
        }

        @Override
        public void rollback(MinecraftServer server, MatchStartStep step) {
            switch (step) {
                case SET_LEGACY_RUNNING -> {
                    setGameState(server, WAITING);
                    if (getGameState(server) != WAITING) {
                        throw new IllegalStateException("legacy scoreboard did not rollback to WAITING state");
                    }
                    matchPhase = MatchPhase.WAITING;
                }
                case SYNC_CLASS_XP -> {
                }
                case START_EXTRACTION -> ExtractionPointManager.reset(server);
                case CAPTURE_PARTICIPANTS -> {
                    matchStartingParticipants = 0;
                    matchHadEnoughPlayers = false;
                }
                case START_VOICE_MATCH -> VoiceChatTeamManager.endMatch(server);
                case INITIALIZE_PLAYERS -> rollbackPlayers(server);
                case PREPARE_SAFE_TELEPORT -> SafeTeleport.clearPool();
                case START_ZONE -> ZoneManager.reset(server);
                case ENFORCE_GAME_RULES -> DropControlManager.enforceGameRules(server);
                case ANNOUNCE_MAP_START -> {
                }
                case EXECUTE_START_DATAPACK -> {
                    CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
                    int result = server.getCommands().performPrefixedCommand(source, "function " + RESET_GAME_FUNCTION);
                    requireDatapackCommandSuccess(RESET_GAME_FUNCTION, result);
                }
                case RESET_MATCH_COUNTERS -> {
                    matchStartingParticipants = 0;
                    matchHadEnoughPlayers = false;
                }
                case START_CONTRACTS -> ContractManager.reset(server);
                case START_DISCORD_TRACKING -> {
                    DiscordLeaderboardService.resetMatch();
                    SetMatchRuntime.reset();
                }
                case RESET_AIRDROP_SCHEDULER -> {
                    AirdropManager.resetAutoScheduler();
                    ServerLevel activeAirdropLevel = getOverworld(server);
                    if (activeAirdropLevel != null) {
                        AirdropManager.cancel(activeAirdropLevel);
                    }
                }
                case CONFIGURE_TEAMS -> {
                    ClanWarManager.resetRuntime();
                    TeamMatchManager.reset(server);
                    currentMode = MatchMode.SOLO;
                }
                case RESET_TRANSIENT_RUNTIME -> {
                    matchPhase = MatchPhase.WAITING;
                    startCountdown = -1;
                    postGameDelay = 0;
                    RespawnControlManager.reset(server);
                    LivesManager.resetAll(server);
                    PassiveClassXPManager.clearAll();
                    RtpTimerManager.clearAll();
                }
            }
        }

        @Override
        public void postCommit(MinecraftServer server) throws Exception {
            List<String> matchProgressFailures = recordMatchesPlayedAfterCommit(server);
            broadcast(server, "[WAR] Match started. Choose a class and use RTP.");
            if (!matchProgressFailures.isEmpty()) {
                throw new IllegalStateException("matchesPlayed post-commit update failed for "
                        + matchProgressFailures.size() + " player(s)");
            }
        }

        private void initializePlayers(MinecraftServer server) {
            startTransitionPlayerSetup = true;
            try {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    LivesManager.ensureStarted(player);
                    player.removeTag("war.eliminated");
                    player.removeTag("war.playing");
                    player.addTag("in_lobby");
                    LobbyManager.moveToLobby(player);
                    ContractManager.giveSelectionTrackerIfAvailable(player);
                    ClassXPManager.sync(player);
                }
            } finally {
                startTransitionPlayerSetup = false;
            }
        }

        private void captureCurrentMatchParticipants(MinecraftServer server) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.getTags().contains("war.playing") && LivesManager.isAliveParticipant(player)) {
                    MapSetManager.recordParticipant(server, player.getUUID(), player.getGameProfile().getName());
                    SetMatchRuntime.registerParticipant(player.getUUID(), player.getGameProfile().getName(),
                            TeamMatchManager.getTeam(player));
                }
            }
        }

        private void rollbackPlayers(MinecraftServer server) {
            startTransitionPlayerSetup = false;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.removeTag("war.playing");
                player.removeTag("in_lobby");
                LobbyManager.moveToLobby(player);
                ClassXPManager.sync(player);
            }
        }

        private List<String> recordMatchesPlayedAfterCommit(MinecraftServer server) {
            List<String> failures = new ArrayList<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!MatchAdmissionManager.isCurrentMatchParticipant(player.getUUID())) continue;
                try {
                    PlayerProgressManager.addMatchPlayed(player);
                } catch (Exception exception) {
                    String playerLabel = player.getGameProfile().getName() + " (" + player.getUUID() + ")";
                    failures.add(playerLabel + ": " + exception.getClass().getSimpleName());
                    TacticalTabletMod.LOGGER.warn("Failed to record post-commit matchesPlayed for {}",
                            playerLabel, exception);
                }
            }
            return failures;
        }

        private void requireDatapackCommandSuccess(ResourceLocation functionId, int result) {
            if (result <= 0) {
                throw new IllegalStateException("datapack function " + functionId + " returned " + result);
            }
        }
    }
}

