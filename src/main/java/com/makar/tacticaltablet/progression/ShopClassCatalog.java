package com.makar.tacticaltablet.progression;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Authoritative price and fixed-tier catalog for classes bought with personal coins. */
public final class ShopClassCatalog {
    private static final List<Entry> ENTRIES = List.of(
            new Entry("solider", 50, ClassTier.RARE),
            new Entry("blackops", 250, ClassTier.EPIC),
            new Entry("rebel", 500, ClassTier.LEGEND),
            new Entry("saboteur", 250, ClassTier.EPIC),
            new Entry("dream", 50, ClassTier.RARE),
            new Entry("shahed", 500, ClassTier.LEGEND),
            new Entry("miniboss", 250, ClassTier.EPIC),
            new Entry("cowboy", 50, ClassTier.RARE),
            new Entry("boomguy", 500, ClassTier.LEGEND),
            new Entry("tagilla", 250, ClassTier.EPIC),
            new Entry("killer", 1000, ClassTier.MONSTER),
            new Entry("crossbowman", 500, ClassTier.LEGEND)
    );
    private static final Map<String, Entry> BY_CLASS_KEY = indexEntries();

    private ShopClassCatalog() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Optional<Entry> byClassKey(String classKey) {
        return Optional.ofNullable(BY_CLASS_KEY.get(normalize(classKey)));
    }

    public static boolean contains(String classKey) {
        return BY_CLASS_KEY.containsKey(normalize(classKey));
    }

    public record Entry(String classKey, int price, ClassTier tier) {
    }

    private static Map<String, Entry> indexEntries() {
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            if (entries.put(entry.classKey(), entry) != null) {
                throw new IllegalStateException("Duplicate shop class " + entry.classKey());
            }
        }
        return Map.copyOf(entries);
    }

    private static String normalize(String classKey) {
        return classKey == null ? "" : classKey.trim().toLowerCase(Locale.ROOT);
    }
}
