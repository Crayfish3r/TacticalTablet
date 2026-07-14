package com.makar.tacticaltablet.progression;

import java.util.Arrays;
import java.util.Optional;

/**
 * Authoritative description of the purchasable class tiers. Numeric ids are
 * persisted in player profiles and are therefore deliberately stable.
 */
public enum ClassTier {
    BASIC(0, "basic", "BASIC", 0, 300, 0, ""),
    RARE(1, "rare", "RARE", 300, 800, 50, "_rare"),
    EPIC(2, "epic", "EPIC", 800, 1300, 100, "_epic"),
    LEGEND(3, "legend", "LEGEND", 1300, 2000, 500, "_legend"),
    MONSTER(4, "monster", "MONSTER", 2000, 2000, 1000, "_monster");

    public static final int MAX_XP = MONSTER.xpCap;

    private final int id;
    private final String systemName;
    private final String displayName;
    private final int requiredXp;
    private final int xpCap;
    private final int upgradeCost;
    private final String kitSuffix;

    ClassTier(int id, String systemName, String displayName, int requiredXp, int xpCap, int upgradeCost,
              String kitSuffix) {
        this.id = id;
        this.systemName = systemName;
        this.displayName = displayName;
        this.requiredXp = requiredXp;
        this.xpCap = xpCap;
        this.upgradeCost = upgradeCost;
        this.kitSuffix = kitSuffix;
    }

    public int id() { return id; }
    public String systemName() { return systemName; }
    public String displayName() { return displayName; }
    public int requiredXp() { return requiredXp; }
    public int xpCap() { return xpCap; }
    public int upgradeCost() { return upgradeCost; }
    public String kitSuffix() { return kitSuffix; }

    public boolean isMaximum() { return this == MONSTER; }

    public Optional<ClassTier> next() {
        return id + 1 < values().length ? Optional.of(values()[id + 1]) : Optional.empty();
    }

    public static Optional<ClassTier> byId(int id) {
        return Arrays.stream(values()).filter(tier -> tier.id == id).findFirst();
    }

    public static ClassTier clamp(int id) {
        if (id <= BASIC.id) return BASIC;
        if (id >= MONSTER.id) return MONSTER;
        return values()[id];
    }
}
