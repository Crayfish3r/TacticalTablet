package com.makar.tacticaltablet.anticheat;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.lives.LivesManager;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AntiCheatManager {

    private static final long ALERT_THROTTLE_MS = 5_000L;
    private static final String TAG_IN_LOBBY = "in_lobby";
    private static final String TAG_WAR_PLAYING = "war.playing";
    private static final Map<UUID, Map<ViolationType, Integer>> violations = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<ViolationType, Long>> lastAlertTimes = new ConcurrentHashMap<>();

    private AntiCheatManager() {
    }

    public static void record(ServerPlayer player, ViolationType type, Severity severity, String details) {
        if (player == null || type == null || severity == null) return;

        UUID uuid = player.getUUID();
        int count = violations.computeIfAbsent(uuid, key -> new ConcurrentHashMap<>())
                .merge(type, 1, Integer::sum);

        String phase = getPhase(player);
        String safeDetails = sanitize(details);
        String logLine = "[AC] player=" + player.getGameProfile().getName()
                + " type=" + type
                + " severity=" + severity
                + " phase=" + phase
                + " details=" + safeDetails;

        if (!shouldAlert(type, severity, count)) {
            if (severity == Severity.LOW) {
                TacticalTabletMod.LOGGER.debug(logLine);
            } else {
                TacticalTabletMod.LOGGER.info(logLine);
            }
            return;
        }

        if (!shouldNotify(uuid, type)) return;

        TacticalTabletMod.LOGGER.warn(logLine);
    }

    public static int getViolationCount(ServerPlayer player, ViolationType type) {
        if (player == null || type == null) return 0;

        Map<ViolationType, Integer> playerViolations = violations.get(player.getUUID());
        if (playerViolations == null) return 0;

        return playerViolations.getOrDefault(type, 0);
    }

    public static void reset(ServerPlayer player) {
        if (player == null) return;

        UUID uuid = player.getUUID();
        violations.remove(uuid);
        lastAlertTimes.remove(uuid);
    }

    // Clears all anti-cheat runtime counters. Intended for server shutdown/reset paths only.
    public static void resetAll() {
        violations.clear();
        lastAlertTimes.clear();
    }

    public static String getPhase(ServerPlayer player) {
        if (player == null) return "неизвестно";

        if (LivesManager.isEliminated(player)) {
            return "выбыл";
        }

        if (GameStateManager.isInLobby(player) || player.getTags().contains(TAG_IN_LOBBY)) {
            return "лобби";
        }

        if (player.getTags().contains(TAG_WAR_PLAYING)) {
            return "бой";
        }

        if (!GameStateManager.isRunning(player.server)) {
            return "ожидание";
        }

        return "неизвестно";
    }

    private static boolean shouldAlert(ViolationType type, Severity severity, int count) {
        return switch (type) {
            case ILLEGAL_PICKUP, ILLEGAL_CONTAINER -> false;
            case INVALID_RTP -> severity == Severity.HIGH || severity == Severity.CRITICAL;
            case ILLEGAL_INVENTORY -> severity == Severity.MEDIUM
                    || severity == Severity.HIGH
                    || severity == Severity.CRITICAL
                    || count >= 3;
            case INVALID_TABLET_PACKET, PACKET_SPAM, MOVEMENT_ANOMALY, COMBAT_REACH -> true;
        };
    }

    private static boolean shouldNotify(UUID uuid, ViolationType type) {
        long now = System.currentTimeMillis();
        Map<ViolationType, Long> playerAlerts = lastAlertTimes.computeIfAbsent(uuid, key -> new ConcurrentHashMap<>());
        long last = playerAlerts.getOrDefault(type, 0L);

        if (now - last < ALERT_THROTTLE_MS) {
            return false;
        }

        playerAlerts.put(type, now);
        return true;
    }

    private static String sanitize(String details) {
        if (details == null || details.isBlank()) {
            return "нет";
        }

        StringBuilder safe = new StringBuilder(details.length());
        for (int index = 0; index < details.length(); index++) {
            char character = details.charAt(index);
            if (character == ',') {
                safe.append(';');
            } else if (Character.isISOControl(character)) {
                safe.append("\\u").append(String.format(java.util.Locale.ROOT, "%04x", (int) character));
            } else {
                safe.append(character);
            }
        }
        return safe.toString();
    }
}
