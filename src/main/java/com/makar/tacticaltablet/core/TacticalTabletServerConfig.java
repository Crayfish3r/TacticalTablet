package com.makar.tacticaltablet.core;

import net.minecraftforge.common.ForgeConfigSpec;

public final class TacticalTabletServerConfig {

    public static final ForgeConfigSpec SPEC;
    public static final int DEFAULT_COMBAT_ATTRIBUTION_WINDOW_SECONDS = 15;

    private static final ForgeConfigSpec.BooleanValue PY132_BALANCE_ENABLED;
    private static final ForgeConfigSpec.DoubleValue PY132_DAMAGE_MULTIPLIER;
    private static final ForgeConfigSpec.IntValue COMBAT_ATTRIBUTION_WINDOW_SECONDS;
    private static final ForgeConfigSpec.BooleanValue KILL_FEED_ENABLED;

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
                .comment("Enable TacticalTablet's client kill feed. Keep false while the SBW/TaCZ killbar is enabled.")
                .define("enabled", false);
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
}
