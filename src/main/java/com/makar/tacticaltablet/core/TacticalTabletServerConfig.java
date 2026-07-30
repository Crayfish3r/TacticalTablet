package com.makar.tacticaltablet.core;

import net.minecraftforge.common.ForgeConfigSpec;

public final class TacticalTabletServerConfig {

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue PY132_BALANCE_ENABLED;
    private static final ForgeConfigSpec.DoubleValue PY132_DAMAGE_MULTIPLIER;

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
}
