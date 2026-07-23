package com.makar.tacticaltablet.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

public final class MapVoteCandidatePolicy {

    private MapVoteCandidatePolicy() {
    }

    public static List<String> selectCandidates(
            List<String> fullPool,
            List<String> recentPlayedMaps,
            int candidateCount,
            int cooldownSize,
            RandomGenerator random
    ) {
        Objects.requireNonNull(random, "random");
        List<String> pool = normalizePool(fullPool);
        int targetCount = Math.min(Math.max(0, candidateCount), pool.size());
        if (targetCount == 0) return List.of();

        List<String> recent = normalizeRecentPlayedMaps(pool, recentPlayedMaps, cooldownSize);
        Set<String> blocked = new LinkedHashSet<>();
        for (String map : recent) blocked.add(normalize(map));

        List<String> eligible = new ArrayList<>();
        for (String map : pool) {
            if (!blocked.contains(normalize(map))) eligible.add(map);
        }

        for (String oldestBlockedMap : recent) {
            if (eligible.size() >= targetCount) break;
            addIfMissing(eligible, oldestBlockedMap);
        }

        shuffle(eligible, random);
        return List.copyOf(eligible.subList(0, targetCount));
    }

    public static List<String> recordPlayedMap(
            List<String> fullPool,
            List<String> recentPlayedMaps,
            String playedMap,
            int cooldownSize
    ) {
        List<String> pool = normalizePool(fullPool);
        List<String> recent = new ArrayList<>(
                normalizeRecentPlayedMaps(pool, recentPlayedMaps, cooldownSize));
        String canonical = canonicalMapName(pool, playedMap);
        if (canonical == null || cooldownSize <= 0) return List.copyOf(recent);

        String normalizedPlayedMap = normalize(canonical);
        recent.removeIf(map -> normalize(map).equals(normalizedPlayedMap));
        recent.add(canonical);
        trimOldest(recent, cooldownSize);
        return List.copyOf(recent);
    }

    public static List<String> normalizeRecentPlayedMaps(
            List<String> fullPool,
            List<String> recentPlayedMaps,
            int cooldownSize
    ) {
        List<String> pool = normalizePool(fullPool);
        Map<String, String> canonicalByName = new LinkedHashMap<>();
        for (String map : pool) canonicalByName.put(normalize(map), map);

        List<String> stored = normalizeStoredHistory(recentPlayedMaps, cooldownSize);
        List<String> result = new ArrayList<>();
        for (String map : stored) {
            String canonical = canonicalByName.get(normalize(map));
            if (canonical != null) result.add(canonical);
        }
        return List.copyOf(result);
    }

    public static List<String> normalizeStoredHistory(List<String> recentPlayedMaps, int cooldownSize) {
        if (recentPlayedMaps == null || recentPlayedMaps.isEmpty() || cooldownSize <= 0) {
            return List.of();
        }

        Map<String, String> newestDisplayNameByKey = new LinkedHashMap<>();
        for (String map : recentPlayedMaps) {
            if (map == null || map.isBlank()) continue;
            String displayName = map.trim();
            String key = normalize(displayName);
            newestDisplayNameByKey.remove(key);
            newestDisplayNameByKey.put(key, displayName);
        }

        List<String> result = new ArrayList<>(newestDisplayNameByKey.values());
        trimOldest(result, cooldownSize);
        return List.copyOf(result);
    }

    public static List<String> normalizePool(List<String> fullPool) {
        if (fullPool == null || fullPool.isEmpty()) return List.of();

        Map<String, String> canonicalByName = new LinkedHashMap<>();
        for (String map : fullPool) {
            if (map == null || map.isBlank()) continue;
            String displayName = map.trim();
            canonicalByName.putIfAbsent(normalize(displayName), displayName);
        }
        return List.copyOf(canonicalByName.values());
    }

    public static String canonicalMapName(List<String> fullPool, String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) return null;
        for (String map : normalizePool(fullPool)) {
            if (normalize(map).equals(normalized)) return map;
        }
        return null;
    }

    private static void addIfMissing(List<String> maps, String candidate) {
        String normalizedCandidate = normalize(candidate);
        for (String map : maps) {
            if (normalize(map).equals(normalizedCandidate)) return;
        }
        maps.add(candidate);
    }

    private static void trimOldest(List<String> maps, int maximumSize) {
        int overflow = maps.size() - Math.max(0, maximumSize);
        if (overflow > 0) maps.subList(0, overflow).clear();
    }

    private static void shuffle(List<String> maps, RandomGenerator random) {
        for (int index = maps.size() - 1; index > 0; index--) {
            int other = random.nextInt(index + 1);
            String value = maps.get(index);
            maps.set(index, maps.get(other));
            maps.set(other, value);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
