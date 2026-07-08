package com.makar.tacticaltablet.game.respawn;

import com.makar.tacticaltablet.admin.TestModeManager;
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
import com.makar.tacticaltablet.inventory.InventoryManager;
import com.makar.tacticaltablet.tablet.PlayerTabletState;
import com.makar.tacticaltablet.voice.VoiceChatTeamManager;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

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

    private static final int RTP_DELAY = 20 * 30;
    private static final int RETRY_DELAY = 20 * 3;
    private static final int RTP_PER_TICK = 1;
    private static final int POST_RTP_INVULNERABILITY_TICKS = 20 * 5;

    public static void start(ServerPlayer player) {
        if (player == null) return;
        if (!GameStateManager.isRunning(player.server)) return;
        if (PlayerTabletState.isRtpUsed(player)) return;
        if (!LivesManager.canContinueMatch(player)) return;
        UUID uuid = player.getUUID();

        timers.put(uuid, RTP_DELAY);
        dequeue(uuid);
        LobbyManager.sync(player);
    }

    public static void cancel(ServerPlayer player) {
        if (player == null) return;

        UUID uuid = player.getUUID();
        timers.remove(uuid);
        dequeue(uuid);
    }

    public static void clearAll() {
        timers.clear();
        rtpQueue.clear();
        queuedPlayers.clear();
    }

    public static int getTimeLeft(ServerPlayer player) {
        if (player == null) return 0;
        return timers.getOrDefault(player.getUUID(), 0);
    }

    public static boolean canRtp(ServerPlayer player, boolean notify) {
        if (player == null) return false;

        if (!GameStateManager.isRunning(player.server)) {
            if (notify) player.sendSystemMessage(Component.literal("[WAR] Матч ещё не идёт."));
            return false;
        }

        if (!LivesManager.canContinueMatch(player)) {
            if (notify) player.sendSystemMessage(Component.literal("[WAR] Ты выбыл из матча."));
            return false;
        }

        if (!GameStateManager.isInLobby(player)) {
            if (notify) player.sendSystemMessage(Component.literal("[WAR] RTP доступен только в лобби."));
            return false;
        }

        if (!player.getTags().contains("in_lobby")) {
            if (notify) player.sendSystemMessage(Component.literal("[WAR] Ты не отмечен как игрок лобби."));
            return false;
        }

        if (PlayerTabletState.isRtpUsed(player)) {
            if (notify) player.sendSystemMessage(Component.literal("[WAR] RTP уже использован."));
            return false;
        }

        if (MapSetManager.isClanWarSet()) {
            String clanId = ClanManager.getClanIdForPlayer(player);
            if (!clanId.isBlank() && ClanWarManager.isClanEliminated(clanId)) {
                if (notify) player.sendSystemMessage(Component.literal("[WAR] Клан выбыл из войны кланов."));
                return false;
            }
        }

        int requiredPlayers = TestModeManager.getRequiredPlayers(2);
        if (GameStateManager.onlinePlayers(player.server) < requiredPlayers) {
            if (notify) {
                player.sendSystemMessage(Component.literal("[WAR] Для RTP нужно игроков: "
                        + requiredPlayers + "."));
            }
            return false;
        }

        return true;
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

            if (player == null || PlayerTabletState.isRtpUsed(player) || !LivesManager.canContinueMatch(player)) {
                iterator.remove();
                dequeue(uuid);
                continue;
            }

            if (!canRtp(player, false)) {
                if (GameStateManager.isRunning(server)
                        && GameStateManager.isInLobby(player)
                        && player.getTags().contains("in_lobby")) {
                    entry.setValue(RTP_DELAY);
                } else {
                    iterator.remove();
                    dequeue(uuid);
                }
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
            if (!canRtp(player, false)) continue;

            if (GameStateManager.getCurrentMode().isTeamMode()) {
                if (processTeamRtp(server, player)) {
                    continue;
                }
            }

            boolean success = SafeTeleport.teleport(player);

            if (success) {
                finishRtp(player);
            } else {
                timers.put(uuid, RETRY_DELAY);
                player.sendSystemMessage(Component.literal("[WAR] Безопасная точка RTP не найдена. Повторяем..."));
                LobbyManager.sync(player);
            }
        }
    }

    private static boolean processTeamRtp(MinecraftServer server, ServerPlayer trigger) {
        TeamId teamId = TeamMatchManager.getTeam(trigger);
        if (teamId == null) return false;

        List<ServerPlayer> members = TeamMatchManager.getOnlineTeamMembers(server, teamId)
                .stream()
                .filter(player -> canRtp(player, false))
                .toList();

        if (members.isEmpty()) return false;

        boolean success = SafeTeleport.teleportTeam(members);
        if (!success) {
            for (ServerPlayer member : members) {
                timers.put(member.getUUID(), RETRY_DELAY);
                member.sendSystemMessage(Component.literal("[WAR] Безопасная командная точка RTP не найдена. Повторяем..."));
                LobbyManager.sync(member);
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
        LivesManager.ensureStarted(player);
        PlayerTabletState.setRtpUsed(player);

        player.removeTag("in_lobby");
        player.addTag("war.playing");
        VoiceChatTeamManager.assignPlayerToVoiceGroup(player);
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        player.addEffect(new MobEffectInstance(
                MobEffects.DAMAGE_RESISTANCE,
                POST_RTP_INVULNERABILITY_TICKS,
                255,
                false,
                false,
                true
        ));
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
        }
    }

    private static void dequeue(UUID uuid) {
        if (!queuedPlayers.remove(uuid)) return;
        rtpQueue.remove(uuid);
    }
}

