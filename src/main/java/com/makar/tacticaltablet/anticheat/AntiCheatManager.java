package com.makar.tacticaltablet.anticheat;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.lives.LivesManager;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AntiCheatManager {

    private static final long ALERT_THROTTLE_MS = 5_000L;
    private static final Map<UUID, EnumMap<ViolationType, Integer>> violations = new HashMap<>();
    private static final Map<String, Long> lastAlertTimes = new HashMap<>();

    private AntiCheatManager() {
    }

    public static void record(ServerPlayer player, ViolationType type, Severity severity, String details) {
        if (player == null || type == null || severity == null) return;

        UUID uuid = player.getUUID();
        int count = violations.computeIfAbsent(uuid, key -> new EnumMap<>(ViolationType.class))
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
        alertAdmins(player, type, severity, phase, safeDetails);
    }

    public static int getViolationCount(ServerPlayer player, ViolationType type) {
        if (player == null || type == null) return 0;

        EnumMap<ViolationType, Integer> playerViolations = violations.get(player.getUUID());
        if (playerViolations == null) return 0;

        return playerViolations.getOrDefault(type, 0);
    }

    public static void reset(ServerPlayer player) {
        if (player == null) return;

        UUID uuid = player.getUUID();
        violations.remove(uuid);
        lastAlertTimes.keySet().removeIf(key -> key.startsWith(uuid.toString() + ":"));
    }

    public static void resetAll() {
        violations.clear();
        lastAlertTimes.clear();
    }

    public static String getPhase(ServerPlayer player) {
        if (player == null) return "неизвестно";

        if (LivesManager.isEliminated(player)) {
            return "выбыл";
        }

        if (GameStateManager.isInLobby(player) || player.getTags().contains("in_lobby")) {
            return "лобби";
        }

        if (player.getTags().contains("war.playing")) {
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
            case INVALID_RTP -> severity.ordinal() >= Severity.HIGH.ordinal();
            case ILLEGAL_INVENTORY -> severity.ordinal() >= Severity.MEDIUM.ordinal() || count >= 3;
            case INVALID_TABLET_PACKET, PACKET_SPAM, MOVEMENT_ANOMALY, COMBAT_REACH -> true;
        };
    }

    private static boolean shouldNotify(UUID uuid, ViolationType type) {
        long now = System.currentTimeMillis();
        String key = uuid + ":" + type;
        long last = lastAlertTimes.getOrDefault(key, 0L);

        if (now - last < ALERT_THROTTLE_MS) {
            return false;
        }

        lastAlertTimes.put(key, now);
        return true;
    }

    private static void alertAdmins(
            ServerPlayer player,
            ViolationType type,
            Severity severity,
            String phase,
            String details
    ) {
        Component message = Component.literal("[AC] "
                + player.getGameProfile().getName()
                + ": нарушение=" + type
                + " серьёзность=" + severity
                + " фаза=" + phase
                + " - " + details);

        for (ServerPlayer admin : player.server.getPlayerList().getPlayers()) {
            if (admin.hasPermissions(2)) {
                admin.sendSystemMessage(message);
            }
        }
    }

    private static String sanitize(String details) {
        if (details == null || details.isBlank()) {
            return "нет";
        }

        return details.replace('\n', ' ').replace('\r', ' ');
    }
}
