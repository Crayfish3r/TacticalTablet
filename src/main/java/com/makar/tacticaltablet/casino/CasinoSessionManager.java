package com.makar.tacticaltablet.casino;

import com.makar.tacticaltablet.casino.net.CasinoAnimationPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;
import com.makar.tacticaltablet.core.ModBlocks;
import com.makar.tacticaltablet.casino.net.CasinoSpinResultPacket;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MapSetManager;
import com.makar.tacticaltablet.game.MatchAdmissionDecision;
import com.makar.tacticaltablet.game.MatchAdmissionManager;
import com.makar.tacticaltablet.game.MatchMode;
import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.game.clanwar.ClanWarManager;
import com.makar.tacticaltablet.game.contract.ContractManager;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.game.lobby.LobbyManager;
import com.makar.tacticaltablet.game.team.TeamId;
import com.makar.tacticaltablet.game.team.TeamMatchManager;
import com.makar.tacticaltablet.game.team.VoteManager;
import com.makar.tacticaltablet.moderation.ModerModeManager;
import com.makar.tacticaltablet.progression.CasinoProgressRequest;
import com.makar.tacticaltablet.progression.CasinoProgressResult;
import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.voice.VoiceChatTeamManager;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Authoritative transient casino admission and spin state. All methods are called on the server thread. */
public final class CasinoSessionManager {
    public static final String PLAYER_TAG = "tacticaltablet.casino";
    public static final int ANIMATION_TICKS = 80;
    public static final int RETURN_DELAY_TICKS = 20 * 10;

    private static final CasinoSpinTable SPIN_TABLE = new CasinoSpinTable();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, PendingReturn> PENDING_RETURNS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_SPIN_TICK = new HashMap<>();

    private CasinoSessionManager() {
    }

    public static boolean isInCasino(ServerPlayer player) {
        return player != null && isInCasino(player.getUUID());
    }

    public static boolean isInCasino(UUID playerId) {
        return playerId != null && (SESSIONS.containsKey(playerId) || PENDING_RETURNS.containsKey(playerId));
    }

    /** Active play and the ten-second return grace period are both excluded from match selection. */
    public static boolean isExcludedFromMatch(ServerPlayer player) {
        return isInCasino(player);
    }

    /** Compatibility entry cannot authenticate a physical machine and therefore fails closed. */
    @Deprecated
    public static boolean openFromNpc(ServerPlayer player) { return false; }

    public static boolean openFromMachine(ServerPlayer player, BlockPos pos) {
        if (!canEnterFromMachine(player) || pos == null) {
            if (player != null) player.sendSystemMessage(Component.translatable("message.tacticaltablet.casino.unavailable"));
            return false;
        }
        CasinoMachineBlockEntity machine = machineAt(player, pos);
        if (machine == null || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                > CasinoMachineAccess.MAX_USE_DISTANCE_SQR) return false;
        return open(player, Source.MACHINE, machine);
    }

    public static boolean openFromSpectator(ServerPlayer player) {
        if (!canEnterFromSpectator(player)) {
            if (player != null) player.sendSystemMessage(Component.translatable("message.tacticaltablet.casino.spectator_unavailable"));
            return false;
        }
        return open(player, Source.SPECTATOR, null);
    }

    private static boolean open(ServerPlayer player, Source source, CasinoMachineBlockEntity machine) {
        // Closing an old menu must not close the replacement session in its removed callback.
        player.closeContainer();
        Session session = new Session(UUID.randomUUID(), source, machine);
        SESSIONS.put(player.getUUID(), session);
        PENDING_RETURNS.remove(player.getUUID());
        player.addTag(PLAYER_TAG);
        if (source == Source.MACHINE) {
            VoteManager.removeVote(player);
            if (GameStateManager.getMatchPhase() == MatchPhase.TEAM_SELECT) TeamMatchManager.removePlayerFromMatch(player);
        }
        int balance = PlayerProgressManager.getCoins(player);
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (id, inventory, user) -> new CasinoMenu(id, session.id, balance, session.dimension, session.machinePos),
                Component.translatable("screen.tacticaltablet.casino.title")), data -> {
            data.writeUUID(session.id);
            data.writeVarInt(balance);
            data.writeBoolean(source == Source.SPECTATOR);
            if (source == Source.MACHINE) {
                data.writeResourceLocation(session.dimension.location());
                data.writeBlockPos(session.machinePos);
            }
        });
        if (!(player.containerMenu instanceof CasinoMenu menu) || !menu.sessionId().equals(session.id)) {
            close(player, session.id);
            return false;
        }
        return true;
    }

    private static CasinoMachineBlockEntity machineAt(ServerPlayer player, BlockPos pos) {
        if (!player.serverLevel().hasChunkAt(pos) || !player.level().getBlockState(pos).is(ModBlocks.CASINO_MACHINE.get())) return null;
        return player.level().getBlockEntity(pos) instanceof CasinoMachineBlockEntity machine && !machine.isRemoved() ? machine : null;
    }

    public static boolean isMenuValid(ServerPlayer player, CasinoMenu menu) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || player.containerMenu != menu || !menu.matches(session.id, session.dimension, session.machinePos)
                || player.hasDisconnected() || !player.isAlive() || ModerModeManager.isInModerMode(player)) return false;
        if (session.source == Source.SPECTATOR) {
            return GameStateManager.isInLobby(player) || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR;
        }
        boolean sameDimension = player.level().dimension().equals(session.dimension);
        CasinoMachineBlockEntity actual = sameDimension ? machineAt(player, session.machinePos) : null;
        return CasinoMachineAccess.allows(true, sameDimension, actual != null, actual == session.machine,
                player.distanceToSqr(session.machinePos.getX() + 0.5, session.machinePos.getY() + 0.5,
                        session.machinePos.getZ() + 0.5), true);
    }

    private static void invalidate(ServerPlayer player, Session session) {
        if (player.containerMenu instanceof CasinoMenu menu && menu.sessionId().equals(session.id)) player.closeContainer();
        close(player, session.id);
    }

    public static void spin(ServerPlayer player, UUID sessionId, UUID requestId, int stake) {
        if (player == null || player.hasDisconnected() || sessionId == null || requestId == null) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.id.equals(sessionId)) {
            sendFailure(player, sessionId, requestId, CasinoSpinResultPacket.Status.INVALID_SESSION,
                    PlayerProgressManager.getCoins(player));
            return;
        }
        if (!(player.containerMenu instanceof CasinoMenu menu) || !isMenuValid(player, menu)) {
            invalidate(player, session);
            sendFailure(player, sessionId, requestId, CasinoSpinResultPacket.Status.INVALID_SESSION, PlayerProgressManager.getCoins(player));
            return;
        }
        if (requestId.equals(session.lastRequestId) && session.lastResult != null) {
            PacketHandler.sendToPlayer(player, session.lastResult);
            if (session.lastAnimation != null) PacketHandler.sendToPlayer(player, session.lastAnimation);
            return;
        }
        if (!CasinoSpinTable.isAllowedStake(stake)) {
            sendFailure(player, sessionId, requestId, CasinoSpinResultPacket.Status.INVALID_REQUEST,
                    PlayerProgressManager.getCoins(player));
            return;
        }

        long currentTick = player.server.getTickCount();
        if (currentTick < NEXT_SPIN_TICK.getOrDefault(player.getUUID(), 0L)) {
            sendFailure(player, sessionId, requestId, CasinoSpinResultPacket.Status.BUSY,
                    PlayerProgressManager.getCoins(player));
            return;
        }

        CasinoMachineBlockEntity machine = session.machine;
        if (machine != null && !machine.reserveSpin()) {
            sendFailure(player, sessionId, requestId, CasinoSpinResultPacket.Status.CASINO_MACHINE_BUSY, PlayerProgressManager.getCoins(player));
            return;
        }
        try {
            CasinoReward rolled = SPIN_TABLE.roll(stake);
            CasinoProgressResult committed = PlayerProgressManager.applyCasinoSpin(player, new CasinoProgressRequest(
                    requestId.toString(), stake, rolled.kind(), rolled.coins(), rolled.classId(), rolled.duplicateCompensation()));
            long seed = SECURE_RANDOM.nextLong();
            boolean newlyApplied = committed.status() == CasinoProgressResult.Status.APPLIED;
            CasinoSpinResultPacket result = packetFor(sessionId, requestId, committed, seed, newlyApplied);
            if (committed.successful()) {
                CasinoReelResult reels = CasinoReelResult.fromReward(committed.rewardKind(), committed.awardedCoins(), seed);
                CasinoReelResult previous = machine == null ? session.previousReels : machine.animation().target();
                long start = player.level().getGameTime();
                if (machine != null && newlyApplied) machine.startAnimation(seed, reels);
                else if (machine != null) machine.releaseSpin();
                session.previousReels = reels;
                NEXT_SPIN_TICK.put(player.getUUID(), currentTick + ANIMATION_TICKS);
                session.lastRequestId = requestId;
                session.lastResult = result;
                session.lastAnimation = new CasinoAnimationPacket(sessionId, requestId, start,
                        newlyApplied ? ANIMATION_TICKS : 0, seed, previous, reels);
                ClassXPManager.sync(player);
            } else if (machine != null) machine.releaseSpin();
            PacketHandler.sendToPlayer(player, result);
            if (committed.successful()) PacketHandler.sendToPlayer(player, session.lastAnimation);
        } finally {
            // Releases reservations on failure/exception; an already started visual spin stays busy.
            if (machine != null) machine.releaseSpin();
        }
    }

    public static void close(ServerPlayer player, UUID sessionId) {
        if (player == null) return;
        Session current = SESSIONS.get(player.getUUID());
        if (current == null || sessionId == null || !current.id.equals(sessionId)) return;
        scheduleReturn(player);
    }

    public static void onLogin(ServerPlayer player) {
        if (player == null) return;
        SESSIONS.remove(player.getUUID());
        PENDING_RETURNS.remove(player.getUUID());
        player.removeTag(PLAYER_TAG);
    }

    public static void onLogout(ServerPlayer player) {
        if (player == null) return;
        SESSIONS.remove(player.getUUID());
        PENDING_RETURNS.remove(player.getUUID());
        player.removeTag(PLAYER_TAG);
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        long currentTick = server.getTickCount();
        NEXT_SPIN_TICK.entrySet().removeIf(entry -> entry.getValue() + 200L < currentTick);

        for (Map.Entry<UUID, Session> entry : new java.util.ArrayList<>(SESSIONS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.hasDisconnected()) {
                SESSIONS.remove(entry.getKey());
                PENDING_RETURNS.remove(entry.getKey());
                if (player != null) player.removeTag(PLAYER_TAG);
            } else if (!(player.containerMenu instanceof CasinoMenu menu) || !isMenuValid(player, menu)) {
                invalidate(player, entry.getValue());
            }
        }

        Iterator<Map.Entry<UUID, PendingReturn>> returnIterator = PENDING_RETURNS.entrySet().iterator();
        while (returnIterator.hasNext()) {
            Map.Entry<UUID, PendingReturn> entry = returnIterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.hasDisconnected()) {
                if (player != null) player.removeTag(PLAYER_TAG);
                returnIterator.remove();
                continue;
            }

            CasinoReturnPolicy.Decision decision = CasinoReturnPolicy.decide(
                    currentTick >= entry.getValue().deadlineTick,
                    GameStateManager.isRunning(server),
                    GameStateManager.getMatchPhase(),
                    ModerModeManager.isInModerMode(player),
                    LivesManager.isEliminated(player),
                    ClanWarManager.shouldKeepSpectating(player),
                    MapSetManager.isClanWarSet()
            );
            if (decision == CasinoReturnPolicy.Decision.WAIT) continue;

            returnIterator.remove();
            player.removeTag(PLAYER_TAG);
            switch (decision) {
                case RELEASE_FOR_NEXT_MATCH -> releaseForNextMatch(player);
                case ADMIT_ACTIVE_MATCH -> admitActiveMatch(player);
                case KEEP_SPECTATING -> keepSpectating(player);
                case WAIT -> {
                }
            }
        }
    }

    public static void clear(MinecraftServer server) {
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) player.removeTag(PLAYER_TAG);
        }
        SESSIONS.clear();
        PENDING_RETURNS.clear();
        NEXT_SPIN_TICK.clear();
    }

    private static boolean canEnterFromMachine(ServerPlayer player) {
        if (player == null || player.hasDisconnected()) return false;
        if (!com.makar.tacticaltablet.game.ServerRules.enabled(player.server)) return player.isAlive();
        if (hasOpenReentryWindow(player)) {
            return GameStateManager.isInLobby(player)
                    && !GameStateManager.isStartTransitionPlayerSetup()
                    && !ModerModeManager.isInModerMode(player);
        }
        return CasinoAdmissionPolicy.allowsMachine(
                GameStateManager.isInLobby(player),
                GameStateManager.isRunning(player.server),
                GameStateManager.isStartTransitionPlayerSetup(),
                ModerModeManager.isInModerMode(player),
                GameStateManager.getMatchPhase()
        );
    }

    private static boolean canEnterFromSpectator(ServerPlayer player) {
        if (player == null || player.hasDisconnected()) return false;
        return CasinoAdmissionPolicy.allowsSpectator(
                player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR,
                GameStateManager.isRunning(player.server),
                ModerModeManager.isInModerMode(player),
                GameStateManager.getMatchPhase()
        );
    }

    private static boolean hasOpenReentryWindow(ServerPlayer player) {
        PendingReturn pending = PENDING_RETURNS.get(player.getUUID());
        return pending != null && player.server.getTickCount() < pending.deadlineTick;
    }

    private static void scheduleReturn(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
        if (!com.makar.tacticaltablet.game.ServerRules.enabled(player.server)) {
            PENDING_RETURNS.remove(player.getUUID());
            player.removeTag(PLAYER_TAG);
            return;
        }
        player.addTag(PLAYER_TAG);
        PENDING_RETURNS.put(
                player.getUUID(),
                new PendingReturn(player.server.getTickCount() + RETURN_DELAY_TICKS)
        );
        player.sendSystemMessage(Component.translatable(
                "message.tacticaltablet.casino.return_pending",
                RETURN_DELAY_TICKS / 20
        ));
    }

    private static void releaseForNextMatch(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("message.tacticaltablet.casino.returned_next_match"));
        LobbyManager.sync(player);
    }

    private static void keepSpectating(ServerPlayer player) {
        if (MatchAdmissionManager.isLateSpectator(player)) {
            MatchAdmissionManager.enforceLateSpectator(player, false);
        }
        player.sendSystemMessage(Component.translatable("message.tacticaltablet.casino.return_unavailable"));
        ClassXPManager.sync(player);
    }

    private static void admitActiveMatch(ServerPlayer player) {
        MatchAdmissionDecision admission = MatchAdmissionManager.finalizePlayerJoin(player);
        switch (admission.outcome()) {
            case ACTIVE_PARTICIPANT, RETURNING_PARTICIPANT -> completeActiveMatchReturn(player, admission);
            case LATE_SPECTATOR -> {
                MatchAdmissionManager.enforceLateSpectator(player, false);
                player.sendSystemMessage(Component.translatable("message.tacticaltablet.casino.return_unavailable"));
            }
            case NORMAL_LOBBY_PLAYER -> releaseForNextMatch(player);
            case DISCONNECTED -> {
            }
        }
    }

    private static void completeActiveMatchReturn(ServerPlayer player, MatchAdmissionDecision admission) {
        UUID matchId = admission.matchId().orElse(null);
        if (matchId == null || !GameStateManager.isRunning(player.server)
                || GameStateManager.getMatchPhase() != MatchPhase.RUNNING) {
            releaseForNextMatch(player);
            return;
        }

        LivesManager.reconcileMatchStateOnJoin(player);
        if (LivesManager.ensureEliminatedIfOutOfLives(player)) {
            keepSpectating(player);
            return;
        }

        MatchMode mode = GameStateManager.getCurrentMode();
        TeamId assignedTeam = null;
        if (mode.isTeamMode()) {
            assignedTeam = TeamMatchManager.assignLateJoiner(player.server, player, mode);
            if (assignedTeam == null) {
                LivesManager.clearForLateSpectator(player);
                player.setGameMode(GameType.SPECTATOR);
                keepSpectating(player);
                return;
            }
        }

        LivesManager.ensureStarted(player);
        int remainingLives = LivesManager.getLives(player);
        if (mode == MatchMode.SOLO) {
            remainingLives = Math.max(0, remainingLives - 1);
            LivesManager.setLives(player, remainingLives);
        }

        player.setGameMode(GameType.SURVIVAL);
        LobbyManager.moveToLobby(player);
        MapSetManager.sync(player, MapSetManager.isVoting());
        ContractManager.ensureTracker(player);
        ContractManager.giveSelectionTrackerIfAvailable(player);
        TeamMatchManager.applyScoreboardTeams(player.server);
        if (assignedTeam != null) {
            VoiceChatTeamManager.assignPlayerToVoiceGroup(player);
        }
        PlayerProgressManager.ensureMatchPlayed(player, matchId, null);
        ClassXPManager.sync(player);
        player.sendSystemMessage(Component.translatable(
                mode == MatchMode.SOLO
                        ? "message.tacticaltablet.casino.returned_solo"
                        : "message.tacticaltablet.casino.returned_team",
                remainingLives
        ));
    }

    private static CasinoSpinResultPacket packetFor(
            UUID sessionId,
            UUID requestId,
            CasinoProgressResult result, long seed, boolean newlyApplied
    ) {
        CasinoSpinResultPacket.Status status = switch (result.status()) {
            case APPLIED, ALREADY_APPLIED -> CasinoSpinResultPacket.Status.SUCCESS;
            case INSUFFICIENT_COINS -> CasinoSpinResultPacket.Status.INSUFFICIENT_COINS;
            case INVALID_REQUEST -> CasinoSpinResultPacket.Status.INVALID_REQUEST;
            case SAVE_FAILED -> CasinoSpinResultPacket.Status.SAVE_FAILED;
        };
        return new CasinoSpinResultPacket(
                sessionId,
                requestId,
                status,
                result.balance(),
                result.rewardKind(),
                result.awardedCoins(),
                result.classId(),
                result.duplicate(),
                status == CasinoSpinResultPacket.Status.SUCCESS && newlyApplied ? ANIMATION_TICKS : 0,
                seed
        );
    }

    private static void sendFailure(
            ServerPlayer player,
            UUID sessionId,
            UUID requestId,
            CasinoSpinResultPacket.Status status,
            int balance
    ) {
        PacketHandler.sendToPlayer(player, new CasinoSpinResultPacket(
                sessionId,
                requestId,
                status,
                balance,
                CasinoRewardKind.COINS,
                0,
                "",
                false,
                0,
                0L
        ));
    }

    public enum Source { MACHINE, SPECTATOR }

    private static final class Session {
        private final UUID id;
        private final Source source;
        private final ResourceKey<Level> dimension;
        private final BlockPos machinePos;
        private final CasinoMachineBlockEntity machine;
        private CasinoReelResult previousReels = CasinoReelResult.IDLE;
        private CasinoAnimationPacket lastAnimation;
        private UUID lastRequestId;
        private CasinoSpinResultPacket lastResult;

        private Session(UUID id, Source source, CasinoMachineBlockEntity machine) {
            this.id = id;
            this.source = source;
            this.machine = machine;
            this.dimension = machine == null ? null : machine.getLevel().dimension();
            this.machinePos = machine == null ? null : machine.getBlockPos().immutable();
        }
    }

    private record PendingReturn(long deadlineTick) {
    }
}
