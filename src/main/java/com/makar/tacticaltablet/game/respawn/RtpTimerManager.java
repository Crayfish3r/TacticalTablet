package com.makar.tacticaltablet.game.respawn;

import com.makar.tacticaltablet.airdrop.AirdropManager;
import com.makar.tacticaltablet.clan.ClanManager;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MapSetManager;
import com.makar.tacticaltablet.game.clanwar.ClanWarManager;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.game.lobby.LobbyManager;
import com.makar.tacticaltablet.game.extraction.ExtractionPointManager;
import com.makar.tacticaltablet.game.teleport.SafeTeleport;
import com.makar.tacticaltablet.game.team.TeamId;
import com.makar.tacticaltablet.game.team.TeamMatchManager;
import com.makar.tacticaltablet.game.zone.ZoneManager;
import com.makar.tacticaltablet.inventory.InventoryManager;
import com.makar.tacticaltablet.tablet.PlayerTabletState;
import com.makar.tacticaltablet.voice.VoiceChatTeamManager;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

public class RtpTimerManager {

    private static final Map<UUID, Integer> timers = new HashMap<>();
    private static final Queue<UUID> rtpQueue = new ArrayDeque<>();
    private static final Set<UUID> queuedPlayers = new HashSet<>();
    private static final Map<UUID, RtpRequestState> requestStates = new HashMap<>();
    private static final Map<UUID, UUID> requestMatchIds = new HashMap<>();
    private static final Map<UUID, String> lastFailureReasons = new HashMap<>();
    private static final Map<UUID, Map<FailureReason, Long>> lastNoticeTimes = new HashMap<>();

    private static final int RTP_DELAY = 20 * 30;
    private static final int RETRY_DELAY = 20 * 3;
    private static final int RTP_PER_TICK = 1;
    private static final int POST_RTP_PROTECTION_TICKS = 20 * 7;
    private static final long NOTICE_INTERVAL_MILLIS = 12_000L;

    public enum RtpRequestState {
        TIMER,
        QUEUED,
        SEARCHING,
        RETRY_WAIT,
        COMPLETED,
        CANCELLED
    }

    enum RtpEligibilityResult {
        READY,
        RETRYABLE,
        CANCELLED
    }

    private enum FailureReason {
        NO_SAFE_POINT,
        POOL_PREPARING,
        TEMPORARILY_INELIGIBLE
    }

    public static void start(ServerPlayer player) {
        if (player == null) return;
        if (!GameStateManager.isRunning(player.server)) return;
        if (PlayerTabletState.isRtpUsed(player)) return;
        if (!LivesManager.canContinueMatch(player)) return;
        UUID uuid = player.getUUID();

        RtpRequestState currentState = requestStates.get(uuid);
        if (currentState == RtpRequestState.TIMER
                || currentState == RtpRequestState.QUEUED
                || currentState == RtpRequestState.SEARCHING
                || currentState == RtpRequestState.RETRY_WAIT) return;

        timers.put(uuid, RTP_DELAY);
        dequeue(uuid);
        requestStates.put(uuid, RtpRequestState.TIMER);
        UUID matchId = GameStateManager.getLifecycleSnapshot().matchId().orElse(null);
        if (matchId == null) {
            cancelRequest(uuid, "active match id is unavailable");
            return;
        }
        requestMatchIds.put(uuid, matchId);
        lastFailureReasons.remove(uuid);
        LobbyManager.sync(player);
    }

    public static void cancel(ServerPlayer player) {
        if (player == null) return;

        UUID uuid = player.getUUID();
        cancelRequest(uuid, "cancelled");
    }

    public static void clearAll() {
        timers.clear();
        rtpQueue.clear();
        queuedPlayers.clear();
        requestStates.clear();
        requestMatchIds.clear();
        lastFailureReasons.clear();
        lastNoticeTimes.clear();
        PostRtpProtectionManager.clearAll();
    }

    public static int getTimeLeft(ServerPlayer player) {
        if (player == null) return 0;
        return timers.getOrDefault(player.getUUID(), 0);
    }

    public static boolean canRtp(ServerPlayer player, boolean notify) {
        RtpEligibilityResult result = getEligibility(player);
        if (notify && player != null && result != RtpEligibilityResult.READY) {
            player.sendSystemMessage(Component.literal(result == RtpEligibilityResult.CANCELLED
                    ? "[WAR] RTP недоступен для текущего состояния матча."
                    : "[WAR] RTP временно недоступен. Повторите попытку позже."));
        }
        return result == RtpEligibilityResult.READY;
    }

    public static void forceRtp(ServerPlayer player) {
        if (!canRtp(player, true)) {
            LobbyManager.sync(player);
            return;
        }

        UUID uuid = player.getUUID();
        timers.remove(uuid);
        enqueue(uuid);
        LobbyManager.sync(player);
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;

        SafeTeleport.tickPool(server);
        tickTimers(server);
        processQueue(server);
    }

    private static void tickTimers(MinecraftServer server) {
        if (timers.isEmpty()) return;

        Iterator<Map.Entry<UUID, Integer>> iterator = timers.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            UUID uuid = entry.getKey();
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);

            RtpEligibilityResult eligibility = getEligibility(player);
            if (eligibility == RtpEligibilityResult.CANCELLED) {
                iterator.remove();
                dequeue(uuid);
                markCancelled(uuid, "eligibility cancelled");
                continue;
            }
            if (eligibility == RtpEligibilityResult.RETRYABLE) {
                entry.setValue(RETRY_DELAY);
                requestStates.put(uuid, RtpRequestState.RETRY_WAIT);
                lastFailureReasons.put(uuid, "temporarily ineligible");
                continue;
            }

            int time = entry.getValue() - 1;
            if (time <= 0) {
                enqueue(uuid);
                iterator.remove();
            } else {
                entry.setValue(time);
            }
        }
    }

    private static void processQueue(MinecraftServer server) {
        for (int i = 0; i < RTP_PER_TICK && !rtpQueue.isEmpty(); i++) {
            UUID uuid = rtpQueue.poll();
            queuedPlayers.remove(uuid);

            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            RtpEligibilityResult eligibility = getEligibility(player);
            if (eligibility == RtpEligibilityResult.CANCELLED) {
                markCancelled(uuid, "queue eligibility cancelled");
                continue;
            }
            if (eligibility == RtpEligibilityResult.RETRYABLE) {
                scheduleRetry(uuid, "temporarily ineligible");
                notifyRateLimited(player, FailureReason.TEMPORARILY_INELIGIBLE,
                        "[WAR] RTP временно недоступен. Повторяем позже...");
                continue;
            }
            requestStates.put(uuid, RtpRequestState.SEARCHING);

            if (GameStateManager.getCurrentMode().isTeamMode()) {
                if (processTeamRtp(server, player)) {
                    continue;
                }
            }

            boolean success = SafeTeleport.teleport(player);

            if (success) {
                finishRtp(player);
            } else {
                scheduleRetry(uuid, SafeTeleport.isPoolPreparing() ? "pool preparing" : "no safe point");
                notifyRateLimited(player,
                        SafeTeleport.isPoolPreparing() ? FailureReason.POOL_PREPARING : FailureReason.NO_SAFE_POINT,
                        SafeTeleport.isPoolPreparing()
                                ? "[WAR] Пул RTP ещё подготавливается. Повторяем позже..."
                                : "[WAR] Безопасная точка RTP не найдена. Повторяем позже...");
            }
        }
    }

    private static boolean processTeamRtp(MinecraftServer server, ServerPlayer trigger) {
        TeamId teamId = TeamMatchManager.getTeam(trigger);
        if (teamId == null) return false;

        List<ServerPlayer> members = TeamMatchManager.getOnlineTeamMembers(server, teamId).stream()
                .filter(RtpTimerManager::isPendingRtpParticipant)
                .toList();

        if (members.isEmpty()) {
            markCancelled(trigger.getUUID(), "no pending team members");
            return true;
        }

        boolean success = members.size() == 1
                ? SafeTeleport.teleport(members.get(0))
                : SafeTeleport.teleportTeam(members);
        if (!success) {
            for (ServerPlayer member : members) {
                scheduleRetry(member.getUUID(), SafeTeleport.isPoolPreparing() ? "pool preparing" : "no safe team point");
                notifyRateLimited(member,
                        SafeTeleport.isPoolPreparing() ? FailureReason.POOL_PREPARING : FailureReason.NO_SAFE_POINT,
                        "[WAR] Безопасная командная точка RTP не найдена. Повторяем позже...");
            }
            return true;
        }

        for (ServerPlayer member : members) {
            timers.remove(member.getUUID());
            dequeue(member.getUUID());
            finishRtp(member);
        }
        return true;
    }

    private static void finishRtp(ServerPlayer player) {
        UUID uuid = player.getUUID();
        timers.remove(uuid);
        dequeue(uuid);
        requestStates.put(uuid, RtpRequestState.COMPLETED);
        lastFailureReasons.remove(uuid);
        lastNoticeTimes.remove(uuid);
        LivesManager.ensureStarted(player);
        PlayerTabletState.setRtpUsed(player);
        PostRtpProtectionManager.grant(player, POST_RTP_PROTECTION_TICKS);

        player.removeTag("in_lobby");
        player.addTag("war.playing");
        VoiceChatTeamManager.assignPlayerToVoiceGroup(player);
        player.displayClientMessage(Component.literal(
                "[WAR] Защита после высадки: " + (POST_RTP_PROTECTION_TICKS / 20) + " сек."
        ), true);
        AirdropManager.giveCompassToJoiningPlayer(player);
        ExtractionPointManager.giveCompassToActiveParticipant(player);

        if (PlayerTabletState.isKitUsed(player)) {
            InventoryManager.clearTablets(player);
        } else {
            InventoryManager.giveTabletIfMissing(player);
        }

        LobbyManager.sync(player);
    }

    private static void enqueue(UUID uuid) {
        if (queuedPlayers.add(uuid)) {
            rtpQueue.add(uuid);
            requestStates.put(uuid, RtpRequestState.QUEUED);
        }
    }

    private static void dequeue(UUID uuid) {
        if (!queuedPlayers.remove(uuid)) return;
        rtpQueue.remove(uuid);
    }

    private static void scheduleRetry(UUID uuid, String reason) {
        if (uuid == null) return;
        dequeue(uuid);
        timers.put(uuid, RETRY_DELAY);
        requestStates.put(uuid, RtpRequestState.RETRY_WAIT);
        lastFailureReasons.put(uuid, reason == null ? "retryable" : reason);
    }

    private static void cancelRequest(UUID uuid, String reason) {
        if (uuid == null) return;
        timers.remove(uuid);
        dequeue(uuid);
        markCancelled(uuid, reason);
    }

    private static void markCancelled(UUID uuid, String reason) {
        requestStates.put(uuid, RtpRequestState.CANCELLED);
        lastFailureReasons.put(uuid, reason == null ? "cancelled" : reason);
        requestMatchIds.remove(uuid);
        lastNoticeTimes.remove(uuid);
    }

    private static RtpEligibilityResult getEligibility(ServerPlayer player) {
        if (player == null) return RtpEligibilityResult.CANCELLED;
        UUID requestMatchId = requestMatchIds.get(player.getUUID());
        UUID currentMatchId = GameStateManager.getLifecycleSnapshot().matchId().orElse(null);
        boolean sameMatch = requestMatchId == null
                ? LivesManager.isBoundToCurrentMatch(player)
                : requestMatchId.equals(currentMatchId);
        boolean clanEligible = true;
        if (MapSetManager.isClanWarSet()) {
            String clanId = ClanManager.getClanIdForPlayer(player);
            clanEligible = clanId.isBlank() || !ClanWarManager.isClanEliminated(clanId);
        }
        return classifyEligibility(
                true,
                GameStateManager.isRunning(player.server),
                sameMatch,
                LivesManager.canContinueMatch(player) && clanEligible && !player.isSpectator(),
                PlayerTabletState.isRtpUsed(player),
                GameStateManager.isInLobby(player),
                player.getTags().contains("in_lobby")
        );
    }

    static RtpEligibilityResult classifyEligibility(
            boolean online,
            boolean matchRunning,
            boolean sameMatch,
            boolean canContinue,
            boolean rtpUsed,
            boolean inLobbyDimension,
            boolean hasLobbyTag
    ) {
        if (!online || !matchRunning || !sameMatch || !canContinue || rtpUsed) {
            return RtpEligibilityResult.CANCELLED;
        }
        return inLobbyDimension && hasLobbyTag ? RtpEligibilityResult.READY : RtpEligibilityResult.RETRYABLE;
    }

    private static boolean isPendingRtpParticipant(ServerPlayer player) {
        return player != null && isPendingRtpParticipant(
                LivesManager.canContinueMatch(player),
                GameStateManager.isInLobby(player),
                player.getTags().contains("in_lobby"),
                PlayerTabletState.isRtpUsed(player),
                player.isSpectator()
        );
    }

    static boolean isPendingRtpParticipant(
            boolean canContinue,
            boolean inLobbyDimension,
            boolean hasLobbyTag,
            boolean rtpUsed,
            boolean spectator
    ) {
        return canContinue && inLobbyDimension && hasLobbyTag && !rtpUsed && !spectator;
    }

    private static void notifyRateLimited(ServerPlayer player, FailureReason reason, String message) {
        if (player == null) return;
        long now = System.currentTimeMillis();
        Map<FailureReason, Long> notices = lastNoticeTimes.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        long last = notices.getOrDefault(reason, 0L);
        if (now - last < NOTICE_INTERVAL_MILLIS) return;
        notices.put(reason, now);
        player.sendSystemMessage(Component.literal(message));
        LobbyManager.sync(player);
    }

    /** Read-only runtime state for administrative diagnostics and support logs. */
    public static RtpDebugSnapshot debugSnapshot(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) return RtpDebugSnapshot.empty();

        UUID uuid = player.getUUID();
        TeamId teamId = TeamMatchManager.getTeam(player);
        List<ServerPlayer> onlineMembers = teamId == null
                ? List.of()
                : TeamMatchManager.getOnlineTeamMembers(server, teamId);
        long pendingMembers = onlineMembers.stream().filter(RtpTimerManager::isPendingRtpParticipant).count();
        ServerLevel overworld = GameStateManager.getOverworld(server);

        return new RtpDebugSnapshot(
                player.getGameProfile().getName(),
                GameStateManager.getLifecycleSnapshot().matchId().map(UUID::toString).orElse(""),
                GameStateManager.isRunning(server),
                LivesManager.isBoundToCurrentMatch(player),
                LivesManager.isAliveParticipant(player),
                LivesManager.isEliminated(player),
                GameStateManager.isInLobby(player),
                player.getTags().contains("in_lobby"),
                PlayerTabletState.isRtpUsed(player),
                requestStates.getOrDefault(uuid, RtpRequestState.CANCELLED),
                timers.getOrDefault(uuid, 0),
                queuedPlayers.contains(uuid),
                teamId == null ? "" : teamId.name(),
                onlineMembers.size(),
                (int) pendingMembers,
                ZoneManager.getActiveRtpSettings().mode().name(),
                SafeTeleport.getPreparedCount(),
                SafeTeleport.isPoolPreparing(),
                overworld == null ? 0.0D : overworld.getWorldBorder().getSize(),
                lastFailureReasons.getOrDefault(uuid, "")
        );
    }

    public record RtpDebugSnapshot(
            String player,
            String matchId,
            boolean matchRunning,
            boolean participant,
            boolean alive,
            boolean eliminated,
            boolean inLobbyDimension,
            boolean hasLobbyTag,
            boolean rtpUsed,
            RtpRequestState requestState,
            int timerTicks,
            boolean queued,
            String teamId,
            int onlineTeamMembers,
            int pendingTeamMembers,
            String placementMode,
            int preparedPoolSize,
            boolean poolPreparing,
            double worldBorderSize,
            String lastFailureReason
    ) {
        private static RtpDebugSnapshot empty() {
            return new RtpDebugSnapshot("", "", false, false, false, false, false, false,
                    false, RtpRequestState.CANCELLED, 0, false, "", 0, 0,
                    "", 0, false, 0.0D, "unavailable");
        }
    }
}

