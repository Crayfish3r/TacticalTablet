package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.admin.TestModeManager;
import com.makar.tacticaltablet.airdrop.AirdropManager;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.game.lobby.LobbyManager;
import com.makar.tacticaltablet.game.contract.ContractManager;
import com.makar.tacticaltablet.game.respawn.RespawnControlManager;
import com.makar.tacticaltablet.game.respawn.RtpTimerManager;
import com.makar.tacticaltablet.game.teleport.SafeTeleport;
import com.makar.tacticaltablet.game.team.TeamId;
import com.makar.tacticaltablet.game.team.TeamMatchManager;
import com.makar.tacticaltablet.game.team.VoteManager;
import com.makar.tacticaltablet.game.zone.ZoneManager;
import com.makar.tacticaltablet.integration.discord.DiscordLeaderboardService;
import com.makar.tacticaltablet.map.WorldCleanupManager;
import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.progression.PassiveClassXPManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;

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
    private static MatchPhase matchPhase = MatchPhase.WAITING;
    private static MatchMode currentMode = MatchMode.SOLO;

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

    public static MatchPhase getMatchPhase() {
        return matchPhase;
    }

    public static MatchMode getCurrentMode() {
        return currentMode;
    }

    public static int getLivesPerPlayer() {
        return currentMode.livesPerPlayer();
    }

    public static boolean isTabletAvailableInLobby(MinecraftServer server) {
        if (server == null) return false;
        return isRunning(server) || matchPhase == MatchPhase.VOTING || matchPhase == MatchPhase.TEAM_SELECT;
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
                matchPhase = MatchPhase.WAITING;
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

        if (currentMode.isTeamMode()) {
            int aliveTeams = TeamMatchManager.getAliveTeamCount(server);
            if (aliveTeams <= 1) {
                TeamId winningTeam = TeamMatchManager.findWinningTeam(server);
                ServerPlayer winner = TeamMatchManager.findWinningPlayer(server);
                String winnerLabel = winningTeam == null ? "Нет победителя" : winningTeam.displayName();
                endGame(server, winner, winnerLabel, winningTeam);
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

        if (!validateRuntimeRequirements(server)) {
            setGameState(server, WAITING);
            broadcast(server, "[WAR] Старт матча отменён: сервер настроен не полностью. Проверь лог.");
            return;
        }

        RtpTimerManager.clearAll();
        PassiveClassXPManager.clearAll();
        RespawnControlManager.reset(server);
        LivesManager.resetAll(server);
        setGameState(server, RUNNING);
        matchPhase = MatchPhase.RUNNING;
        if (currentMode.isTeamMode()) {
            TeamMatchManager.autoBalance(server, currentMode);
            TeamMatchManager.applyScoreboardTeams(server);
        } else {
            TeamMatchManager.reset(server);
        }
        AirdropManager.resetAutoScheduler();
        DiscordLeaderboardService.startMatch(server);
        ContractManager.onMatchStart(server);
        matchHadEnoughPlayers = false;
        matchStartingParticipants = 0;

        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, "function " + START_GAME_FUNCTION);
        ZoneManager.start(server);

        SafeTeleport.preparePool(server);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            LivesManager.ensureStarted(player);
            PlayerProgressManager.addMatchPlayed(player);
            player.removeTag("war.eliminated");
            player.removeTag("war.playing");
            player.addTag("in_lobby");
            LobbyManager.moveToLobby(player);
            ContractManager.giveSelectionTrackerIfAvailable(player);
            ClassXPManager.sync(player);
        }

        matchStartingParticipants = playingPlayers(server);
        matchHadEnoughPlayers = matchStartingParticipants >= TestModeManager.getRequiredPlayers(MIN_PLAYERS);

        ClassXPManager.syncAll(server);

        broadcast(server, "[WAR] Матч начался. Выбери класс и используй RTP.");
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
        if (server == null) return;

        matchHadEnoughPlayers = false;
        matchStartingParticipants = 0;
        startCountdown = -1;
        postGameDelay = POST_GAME_DELAY_SECONDS;
        matchPhase = MatchPhase.POST_GAME;
        setGameState(server, WAITING);

        ContractManager.finishMatch(server);
        DiscordLeaderboardService.sendCurrentMatchLeaderboard(server, winner);

        if (winner != null) {
            PlayerProgressManager.addWin(winner);
            PlayerProgressManager.addCoins(winner, PlayerProgressManager.WIN_COIN_REWARD);
            ClassXPManager.addXPToAllClasses(winner, WIN_XP_ALL_CLASSES);
            PlayerProgressManager.savePlayer(winner);
        }

        showWinnerTitle(server, winnerName, winnerTeam);
    }

    public static void resetRuntime(MinecraftServer server) {
        matchHadEnoughPlayers = false;
        matchStartingParticipants = 0;
        tickCounter = 0;
        startCountdown = -1;
        postGameDelay = 0;
        matchPhase = MatchPhase.WAITING;
        currentMode = MatchMode.SOLO;
        VoteManager.reset();
        TeamMatchManager.reset(server);

        if (server != null) {
            setGameState(server, WAITING);
        }
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
        matchPhase = MatchPhase.WAITING;

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
        TeamMatchManager.cleanupScoreboardTeams(server);
        AirdropManager.resetAutoScheduler();
        ContractManager.reset(server);
        ServerLevel activeAirdropLevel = getOverworld(server);
        if (activeAirdropLevel != null) {
            AirdropManager.cancel(activeAirdropLevel);
        }
        ZoneManager.reset(server);
        RespawnControlManager.reset(server);
        PassiveClassXPManager.clearAll();
        RtpTimerManager.clearAll();
        SafeTeleport.clearPool();

        WorldCleanupManager.clearDroppedItems(server);

        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, "function " + RESET_GAME_FUNCTION);

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

    public static boolean forceStartVoting(MinecraftServer server) {
        if (server == null || isRunning(server)) return false;

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

    public static boolean forceStartTeamSelect(MinecraftServer server, MatchMode mode) {
        if (server == null || isRunning(server) || mode == null || !mode.isTeamMode()) return false;

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

    private static void handleWaitingTick(MinecraftServer server) {
        matchHadEnoughPlayers = false;

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
            startCountdown = -1;
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
            currentMode = MatchMode.SOLO;
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
        MutableComponent title = Component.literal(noWinner ? "НЕТ ПОБЕДИТЕЛЯ" : winnerName + " ПОБЕЖДАЕТ!");
        Component subtitle = Component.literal(noWinner ? "Матч завершён." : "+10 опыта стандартным классам, +5 монет");
        MutableComponent chat = Component.literal(noWinner ? "[WAR] Матч завершён без победителя." : "[WAR] ");

        if (!noWinner && winnerTeam != null) {
            title.withStyle(winnerTeam.chatColor());
            chat.append(Component.literal(winnerName).withStyle(winnerTeam.chatColor()))
                    .append(Component.literal(" побеждает!"));
        } else if (!noWinner) {
            chat.append(Component.literal(winnerName + " побеждает!"));
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

    private static String describeVoteModes(MinecraftServer server) {
        int online = onlinePlayers(server);
        return MatchMode.selectableModes(online, TestModeManager.canBypassTeamModeMinimums())
                .stream()
                .map(MatchMode::displayName)
                .reduce((left, right) -> left + " / " + right)
                .orElse(MatchMode.SOLO.displayName());
    }
}

