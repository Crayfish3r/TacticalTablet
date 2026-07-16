package com.makar.tacticaltablet.progression;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable class catalog supplied by the legacy facade. */
record ProgressCatalog(
        Set<String> initialBaseClasses,
        Set<String> baseClasses,
        Map<String, Integer> shopPrices,
        Map<String, Integer> shopLevels,
        Set<String> exclusiveClasses,
        int baseUnlockCost
) {
    ProgressCatalog {
        initialBaseClasses = normalizedSet(initialBaseClasses);
        baseClasses = normalizedSet(baseClasses);
        shopPrices = normalizedMap(shopPrices);
        shopLevels = normalizedMap(shopLevels);
        exclusiveClasses = normalizedSet(exclusiveClasses);
    }

    String normalizeClassId(String classId) {
        return classId == null ? "" : classId.trim().toLowerCase(Locale.ROOT);
    }

    boolean isBaseClass(String classId) {
        return baseClasses.contains(normalizeClassId(classId));
    }

    boolean isInitialBaseClass(String classId) {
        return initialBaseClasses.contains(normalizeClassId(classId));
    }

    boolean isUnlockableBaseClass(String classId) {
        String normalized = normalizeClassId(classId);
        return isBaseClass(normalized) && !isInitialBaseClass(normalized);
    }

    boolean isShopClass(String classId) {
        return shopPrices.containsKey(normalizeClassId(classId));
    }

    boolean isExclusiveClass(String classId) {
        return exclusiveClasses.contains(normalizeClassId(classId));
    }

    int shopPrice(String classId) {
        return shopPrices.getOrDefault(normalizeClassId(classId), 0);
    }

    private static Set<String> normalizedSet(Set<String> values) {
        return values.stream()
                .map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Map<String, Integer> normalizedMap(Map<String, Integer> values) {
        return values.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                entry -> entry.getKey().trim().toLowerCase(Locale.ROOT),
                Map.Entry::getValue
        ));
    }
}
