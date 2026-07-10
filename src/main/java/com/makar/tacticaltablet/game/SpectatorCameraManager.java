package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.game.clanwar.ClanWarManager;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.game.team.TeamId;
import com.makar.tacticaltablet.game.team.TeamMatchManager;
import com.makar.tacticaltablet.moderation.ModerModeManager;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.tablet.net.SpectatorCameraLockStatePacket;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpectatorCameraManager {

    private static final int ENFORCE_INTERVAL_TICKS = 10;
    private static final int SWITCH_COOLDOWN_TICKS = 5;
    private static final Map<UUID, UUID> spectatorTargets = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> switchCooldownTicks = new ConcurrentHashMap<>();
    private static int tickCounter = 0;

    private SpectatorCameraManager() {
    }

    public static void onPlayerEliminated(ServerPlayer player) {
        if (player == null) return;

        if (!shouldLockSpectator(player)) {
            clearSpectatorCameraState(player);
            return;
        }

        ServerPlayer target = resolveCameraTarget(player);
        if (target == null) {
            clearSpectatorCameraState(player);
            return;
        }

        spectatorTargets.put(player.getUUID(), target.getUUID());
        sendLockState(player, true);
        forceCamera(player, target);
    }

    private static ServerPlayer resolveCameraTarget(ServerPlayer player) {
        ServerPlayer target = getCurrentValidCameraTarget(player);
        if (target == null) {
            target = getStoredValidTarget(player.server, player);
        }
        if (target == null) {
            target = selectInitialTarget(player);
        }
        return target;
    }

    public static void onServerTick(MinecraftServer server) {
        if (server == null) return;

        tickSwitchCooldowns();

        if (++tickCounter < ENFORCE_INTERVAL_TICKS) return;
        tickCounter = 0;

        if (!isActiveMatch(server)) {
            clear(server);
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!shouldLockSpectator(player)) {
                clearSpectatorCameraState(player);
                continue;
            }

            ServerPlayer target = resolveCameraTarget(player);

            if (target == null) {
                clearSpectatorCameraState(player);
                continue;
            }

            UUID oldTargetUuid = spectatorTargets.get(player.getUUID());
            spectatorTargets.put(player.getUUID(), target.getUUID());
            if (!target.getUUID().equals(oldTargetUuid)) {
                sendLockState(player, true);
            }
            forceCamera(player, target);
        }
    }

    public static void onPlayerDeath(ServerPlayer player) {
        if (player == null) return;

        clearSpectatorCameraState(player);
        retargetViewersOf(player.server, player.getUUID());
    }

    public static void switchCamera(ServerPlayer spectator, int direction) {
        if (direction == 0 || !shouldLockSpectator(spectator)) return;

        UUID spectatorUuid = spectator.getUUID();
        if (isSwitchOnCooldown(spectatorUuid)) return;

        List<ServerPlayer> targets = getAvailableTargets(spectator);
        if (targets.isEmpty()) {
            clearSpectatorCameraState(spectator);
            return;
        }

        UUID currentUuid = spectatorTargets.get(spectatorUuid);
        if (currentUuid == null) {
            ServerPlayer currentCamera = getCurrentValidCameraTarget(spectator);
            if (currentCamera != null) {
                currentUuid = currentCamera.getUUID();
            }
        }

        int currentIndex = indexOfTarget(targets, currentUuid);
        if (currentIndex < 0) {
            currentIndex = initialTargetIndex(spectator, targets.size());
        }

        ServerPlayer nextTarget = targets.get(Math.floorMod(currentIndex + direction, targets.size()));
        spectatorTargets.put(spectatorUuid, nextTarget.getUUID());
        sendLockState(spectator, true);
        forceCamera(spectator, nextTarget);
        setSwitchCooldown(spectatorUuid);
    }

    public static void onPlayerTeamChanged(ServerPlayer player) {
        if (player == null) return;

        if (shouldLockSpectator(player)) {
            onPlayerEliminated(player);
        }

        retargetViewersOf(player.server, player.getUUID());
    }

    public static void onMatchEnd(MinecraftServer server) {
        clear(server);
    }

    public static void clear(MinecraftServer server) {
        if (server != null) {
            for (UUID spectatorUuid : new ArrayList<>(spectatorTargets.keySet())) {
                ServerPlayer spectator = server.getPlayerList().getPlayer(spectatorUuid);
                if (spectator != null) {
                    clearSpectatorCameraState(spectator);
                }
            }
        }
        spectatorTargets.clear();
        switchCooldownTicks.clear();
        tickCounter = 0;
    }

    private static void retargetViewersOf(MinecraftServer server, UUID oldTargetUuid) {
        if (server == null || oldTargetUuid == null) return;

        for (Iterator<Map.Entry<UUID, UUID>> iterator = spectatorTargets.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<UUID, UUID> entry = iterator.next();
            if (!oldTargetUuid.equals(entry.getValue())) continue;

            ServerPlayer spectator = server.getPlayerList().getPlayer(entry.getKey());
            if (spectator == null || !shouldLockSpectator(spectator)) {
                if (spectator != null) {
                    clearSpectatorCameraState(spectator);
                } else {
                    switchCooldownTicks.remove(entry.getKey());
                    iterator.remove();
                }
                continue;
            }

            ServerPlayer replacement = selectNextTargetAfter(spectator, oldTargetUuid);
            if (replacement == null) {
                clearSpectatorCameraState(spectator);
                continue;
            }

            entry.setValue(replacement.getUUID());
            sendLockState(spectator, true);
            forceCamera(spectator, replacement);
        }
    }

    public static List<ServerPlayer> getAvailableTargets(ServerPlayer spectator) {
        if (spectator == null || spectator.server == null) return List.of();

        if (GameStateManager.getCurrentMode().isTeamMode()) {
            List<ServerPlayer> teammates = new ArrayList<>();
            TeamId teamId = TeamMatchManager.getTeam(spectator);
            if (teamId != null) {
                for (ServerPlayer candidate : TeamMatchManager.getOnlineTeamMembers(spectator.server, teamId)) {
                    if (isAliveCameraTarget(spectator, candidate)
                            && TeamMatchManager.areTeammates(spectator, candidate)) {
                        teammates.add(candidate);
                    }
                }
            }

            if (!teammates.isEmpty()) {
                return teammates;
            }
        }

        List<ServerPlayer> targets = new ArrayList<>();
        for (ServerPlayer candidate : spectator.server.getPlayerList().getPlayers()) {
            if (isAliveCameraTarget(spectator, candidate)) {
                targets.add(candidate);
            }
        }
        return targets;
    }

    private static ServerPlayer getStoredValidTarget(MinecraftServer server, ServerPlayer spectator) {
        UUID targetUuid = spectatorTargets.get(spectator.getUUID());
        if (targetUuid == null) return null;

        ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
        if (containsTarget(getAvailableTargets(spectator), targetUuid) && isAliveCameraTarget(spectator, target)) {
            return target;
        }
        return null;
    }

    private static ServerPlayer getCurrentValidCameraTarget(ServerPlayer spectator) {
        if (spectator == null) return null;

        Entity camera = spectator.getCamera();
        if (camera instanceof ServerPlayer target
                && containsTarget(getAvailableTargets(spectator), target.getUUID())
                && isAliveCameraTarget(spectator, target)) {
            return target;
        }
        return null;
    }

    private static ServerPlayer selectInitialTarget(ServerPlayer spectator) {
        List<ServerPlayer> targets = getAvailableTargets(spectator);
        if (targets.isEmpty()) return null;
        return targets.get(initialTargetIndex(spectator, targets.size()));
    }

    private static ServerPlayer selectNextTargetAfter(ServerPlayer spectator, UUID oldTargetUuid) {
        List<ServerPlayer> targets = getAvailableTargets(spectator);
        if (targets.isEmpty()) return null;

        int oldIndex = indexOfTarget(targets, oldTargetUuid);
        if (oldIndex >= 0) {
            return targets.get(Math.floorMod(oldIndex + 1, targets.size()));
        }

        int oldOrderIndex = indexOfTarget(getTargetOrder(spectator), oldTargetUuid);
        if (oldOrderIndex >= 0) {
            ServerPlayer nextByOrder = firstTargetAfterOrderIndex(spectator, targets, oldOrderIndex);
            if (nextByOrder != null) {
                return nextByOrder;
            }
        }

        return targets.get(initialTargetIndex(spectator, targets.size()));
    }

    private static List<ServerPlayer> getTargetOrder(ServerPlayer spectator) {
        if (spectator == null || spectator.server == null) return List.of();

        if (GameStateManager.getCurrentMode().isTeamMode()) {
            TeamId teamId = TeamMatchManager.getTeam(spectator);
            if (teamId != null) {
                List<ServerPlayer> teammates = new ArrayList<>();
                for (ServerPlayer teammate : TeamMatchManager.getOnlineTeamMembers(spectator.server, teamId)) {
                    if (isAliveCameraTarget(spectator, teammate)
                            && TeamMatchManager.areTeammates(spectator, teammate)) {
                        teammates.add(teammate);
                    }
                }
                if (!teammates.isEmpty()) return teammates;
            }
        }

        return spectator.server.getPlayerList().getPlayers();
    }

    private static ServerPlayer firstTargetAfterOrderIndex(
            ServerPlayer spectator,
            List<ServerPlayer> targets,
            int oldOrderIndex
    ) {
        List<ServerPlayer> order = getTargetOrder(spectator);
        if (order.isEmpty()) return null;

        for (int offset = 1; offset <= order.size(); offset++) {
            ServerPlayer candidate = order.get(Math.floorMod(oldOrderIndex + offset, order.size()));
            if (containsTarget(targets, candidate.getUUID())) {
                return candidate;
            }
        }
        return null;
    }

    private static int initialTargetIndex(ServerPlayer spectator, int targetCount) {
        if (spectator == null || targetCount <= 0) return 0;
        return Math.floorMod(spectator.getUUID().hashCode(), targetCount);
    }

    private static int indexOfTarget(List<ServerPlayer> targets, UUID targetUuid) {
        if (targets == null || targetUuid == null) return -1;
        for (int i = 0; i < targets.size(); i++) {
            if (targetUuid.equals(targets.get(i).getUUID())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean containsTarget(List<ServerPlayer> targets, UUID targetUuid) {
        return indexOfTarget(targets, targetUuid) >= 0;
    }

    private static boolean isSwitchOnCooldown(UUID spectatorUuid) {
        return spectatorUuid != null && switchCooldownTicks.getOrDefault(spectatorUuid, 0) > 0;
    }

    private static void setSwitchCooldown(UUID spectatorUuid) {
        if (spectatorUuid != null) {
            switchCooldownTicks.put(spectatorUuid, SWITCH_COOLDOWN_TICKS);
        }
    }

    private static void tickSwitchCooldowns() {
        switchCooldownTicks.replaceAll((uuid, ticks) -> ticks - 1);
        switchCooldownTicks.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }

    private static boolean isAliveCameraTarget(ServerPlayer spectator, ServerPlayer target) {
        if (spectator == null || target == null) return false;
        if (spectator.getUUID().equals(target.getUUID())) return false;
        if (!LivesManager.isAliveParticipant(target)) return false;
        if (!target.getTags().contains("war.playing")) return false;
        if (target.getTags().contains("in_lobby")) return false;
        if (target.isDeadOrDying()) return false;
        if (target.isSpectator()) return false;
        if (LivesManager.isEliminated(target)) return false;
        return true;
    }

    private static boolean shouldLockSpectator(ServerPlayer player) {
        if (ModerModeManager.isInModerMode(player)) return false;

        return player != null
                && isActiveMatch(player.server)
                && isStrictSpectatorCameraMatch()
                && (LivesManager.isEliminated(player) || player.getTags().contains(ClanWarManager.TAG_SPECTATING))
                && player.isSpectator();
    }

    private static boolean isStrictSpectatorCameraMatch() {
        return MapSetManager.isCompetitiveSet() || MapSetManager.isClanWarSet();
    }

    private static boolean isActiveMatch(MinecraftServer server) {
        return server != null
                && GameStateManager.isRunning(server)
                && GameStateManager.getMatchPhase() == MatchPhase.RUNNING;
    }

    private static void forceCamera(ServerPlayer spectator, Entity target) {
        if (spectator == null || target == null) return;
        if (spectator.getCamera() != target) {
            spectator.setCamera(target);
        }
    }

    private static void clearSpectatorCameraState(ServerPlayer player) {
        if (player == null) return;

        boolean hadTarget = spectatorTargets.remove(player.getUUID()) != null;
        boolean hadCooldown = switchCooldownTicks.remove(player.getUUID()) != null;
        boolean cameraChanged = player.getCamera() != player;

        if (hadTarget || hadCooldown || cameraChanged) {
            sendLockState(player, false);
        }

        if (cameraChanged) {
            player.setCamera(player);
        }
    }

    private static void sendLockState(ServerPlayer player, boolean locked) {
        if (player != null) {
            PacketHandler.sendToPlayer(player, new SpectatorCameraLockStatePacket(locked));
        }
    }
}
