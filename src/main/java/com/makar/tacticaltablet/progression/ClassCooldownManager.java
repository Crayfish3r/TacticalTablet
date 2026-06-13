package com.makar.tacticaltablet.progression;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class ClassCooldownManager {

    private static final int SNIPER_CLASS_ID = 1;

    private static final long[] COOLDOWNS = new long[]{
            minutes(2),
            minutes(2),
            minutes(4),
            minutes(15),
            minutes(10),
            minutes(6),
            minutes(15),
            0L,
            minutes(10),
            minutes(13),
            minutes(15),
            minutes(15),
            minutes(15),
            minutes(5),
            minutes(15)
    };

    private static final Map<UUID, Map<Integer, Long>> data = new HashMap<>();

    private static long getCooldownTime(int classId) {
        if (classId < 0 || classId >= COOLDOWNS.length) {
            return 0L;
        }

        return COOLDOWNS[classId];
    }

    private static long getCooldownTime(ServerPlayer player, int classId) {
        if (classId != SNIPER_CLASS_ID) {
            return getCooldownTime(classId);
        }

        int tier = PlayerProgressManager.getLevel(player, "sniper");
        if (tier >= PlayerProgressManager.LEGEND_TIER) {
            return minutes(15);
        }

        if (tier >= PlayerProgressManager.EPIC_TIER) {
            return minutes(10);
        }

        return minutes(2);
    }

    private static long minutes(int minutes) {
        return minutes * 60L * 1000L;
    }

    public static void setCooldown(ServerPlayer player, int classId) {
        if (player == null || classId < 0 || classId >= COOLDOWNS.length) return;

        long cooldownTime = getCooldownTime(player, classId);
        if (cooldownTime <= 0L) return;

        data.computeIfAbsent(player.getUUID(), key -> new HashMap<>())
                .put(classId, System.currentTimeMillis() + cooldownTime);
    }

    public static long getRemaining(ServerPlayer player, int classId) {
        if (player == null || classId < 0 || classId >= COOLDOWNS.length) return 0L;

        Map<Integer, Long> map = data.get(player.getUUID());
        if (map == null) return 0L;

        Long endTime = map.get(classId);
        if (endTime == null) return 0L;

        long left = endTime - System.currentTimeMillis();
        if (left <= 0L) {
            map.remove(classId);
            if (map.isEmpty()) {
                data.remove(player.getUUID());
            }

            return 0L;
        }

        return left;
    }

    public static boolean isOnCooldown(ServerPlayer player, int classId) {
        return getRemaining(player, classId) > 0L;
    }

    public static Map<Integer, Long> getCooldowns(ServerPlayer player) {
        Map<Integer, Long> result = new HashMap<>();
        if (player == null) return result;

        Map<Integer, Long> map = data.get(player.getUUID());
        if (map == null || map.isEmpty()) return result;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, Long>> iterator = map.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Integer, Long> entry = iterator.next();
            int classId = entry.getKey();
            long left = entry.getValue() - now;

            if (left <= 0L) {
                iterator.remove();
                continue;
            }

            result.put(classId, left);
        }

        if (map.isEmpty()) {
            data.remove(player.getUUID());
        }

        return result;
    }

    public static void reset(ServerPlayer player) {
        if (player == null) return;
        data.remove(player.getUUID());
    }

    public static void resetAll() {
        data.clear();
    }
}
