package com.makar.tacticaltablet.core;

import net.minecraftforge.common.ForgeConfigSpec;

public final class TacticalTabletServerConfig {

    public static final ForgeConfigSpec SPEC;
    public static final int DEFAULT_COMBAT_ATTRIBUTION_WINDOW_SECONDS = 15;

    private static final ForgeConfigSpec.BooleanValue PY132_BALANCE_ENABLED;
    private static final ForgeConfigSpec.DoubleValue PY132_DAMAGE_MULTIPLIER;
    private static final ForgeConfigSpec.IntValue COMBAT_ATTRIBUTION_WINDOW_SECONDS;
    private static final ForgeConfigSpec.BooleanValue KILL_FEED_ENABLED;
    private static final ForgeConfigSpec.IntValue KILL_FEED_CONFIG_VERSION;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("py132Balance");
        PY132_BALANCE_ENABLED = builder
                .comment("Whether damage from the PY132A laser damage type is adjusted.")
                .define("enabled", true);
        PY132_DAMAGE_MULTIPLIER = builder
                .comment("Multiplier applied to ts:laserhit damage.")
                .defineInRange("damageMultiplier", 0.35D, 0.0D, 1.0D);
        builder.pop();

        builder.push("combatAttribution");
        COMBAT_ATTRIBUTION_WINDOW_SECONDS = builder
                .comment("How long a PvP hit can attribute a later environmental or periodic-damage death.")
                .defineInRange("windowSeconds", DEFAULT_COMBAT_ATTRIBUTION_WINDOW_SECONDS, 1, 120);
        builder.pop();

        builder.push("killFeed");
        KILL_FEED_ENABLED = builder
                .comment("Enable TacticalTablet's primary match kill feed. Disable the SBW/TaCZ killbar separately.")
                .define("enabled", true);
        KILL_FEED_CONFIG_VERSION = builder
                .comment("Internal migration marker. Do not edit manually.")
                .defineInRange("configVersion", 0, 0, 1);
        builder.pop();

        SPEC = builder.build();
    }

    private TacticalTabletServerConfig() {
    }

    public static boolean isPy132BalanceEnabled() {
        return PY132_BALANCE_ENABLED.get();
    }

    public static float getPy132DamageMultiplier() {
        return PY132_DAMAGE_MULTIPLIER.get().floatValue();
    }

    public static int getCombatAttributionWindowTicks() {
        return COMBAT_ATTRIBUTION_WINDOW_SECONDS.get() * 20;
    }

    public static boolean isKillFeedEnabled() {
        return KILL_FEED_ENABLED.get();
    }

    /**
     * Version 0 was generated while the kill feed defaulted to false. Upgrade it once so existing
     * worlds receive the new primary UI; explicit administrator changes are respected afterwards.
     */
    public static boolean migrateKillFeedDefault() {
        if (KILL_FEED_CONFIG_VERSION.get() >= 1) return false;
        KILL_FEED_ENABLED.set(true);
        KILL_FEED_CONFIG_VERSION.set(1);
        return true;
    }
}
