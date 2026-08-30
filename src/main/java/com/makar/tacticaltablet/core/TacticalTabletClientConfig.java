package com.makar.tacticaltablet.core;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

/** Client-owned visual settings. These values never require server permission. */
public final class TacticalTabletClientConfig {
    public enum StaminaHudSide { AUTO, LEFT, RIGHT }

    public static final String DEFAULT_JOIN_SERVER_ADDRESS = "deluxewarfare.sosal.today";
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.ConfigValue<String> JOIN_SERVER_ADDRESS;
    public static final ForgeConfigSpec.BooleanValue MDC_STAMINA_HUD_ENABLED;
    public static final ForgeConfigSpec.EnumValue<StaminaHudSide> MDC_STAMINA_HUD_SIDE;
    public static final ForgeConfigSpec.IntValue MDC_STAMINA_HUD_X_OFFSET;
    public static final ForgeConfigSpec.IntValue MDC_STAMINA_HUD_Y_OFFSET;
    public static final ForgeConfigSpec.DoubleValue MDC_STAMINA_HUD_SCALE;
    public static final ForgeConfigSpec.DoubleValue MDC_STAMINA_HUD_OPACITY;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("mainMenu");
        JOIN_SERVER_ADDRESS = builder
                .comment("Server address used by the main-menu Join button (host or host:port).")
                .define("joinServerAddress", DEFAULT_JOIN_SERVER_ADDRESS);
        builder.pop();

        builder.push("modernDamageControl");
        MDC_STAMINA_HUD_ENABLED = builder
                .comment("Render TacticalTablet arm and leg stamina bars when supported MDC is installed.")
                .define("staminaHudEnabled", true);
        MDC_STAMINA_HUD_SIDE = builder
                .comment("Preferred side; AUTO selects a free safe-area candidate.")
                .defineEnum("staminaHudSide", StaminaHudSide.AUTO);
        MDC_STAMINA_HUD_X_OFFSET = builder
                .comment("Horizontal offset in GUI-scaled pixels after safe-area placement.")
                .defineInRange("staminaHudXOffset", 0, -200, 200);
        MDC_STAMINA_HUD_Y_OFFSET = builder
                .comment("Vertical offset in GUI-scaled pixels after safe-area placement.")
                .defineInRange("staminaHudYOffset", 0, -120, 120);
        MDC_STAMINA_HUD_SCALE = builder
                .comment("TacticalTablet MDC stamina HUD scale.")
                .defineInRange("staminaHudScale", 1.0D, 0.5D, 2.0D);
        MDC_STAMINA_HUD_OPACITY = builder
                .comment("TacticalTablet MDC stamina HUD opacity.")
                .defineInRange("staminaHudOpacity", 0.9D, 0.25D, 1.0D);
        builder.pop();
        SPEC = builder.build();
    }

    private TacticalTabletClientConfig() {
    }

    public static String getJoinServerAddress() {
        return JOIN_SERVER_ADDRESS.get();
    }

    public static void setJoinServerAddress(String address) {
        JOIN_SERVER_ADDRESS.set(address);
        save();
    }

    public static void save() {
        ConfigTracker.INSTANCE.configSets().getOrDefault(ModConfig.Type.CLIENT, java.util.Set.of())
                .stream()
                .filter(config -> TacticalTabletMod.MODID.equals(config.getModId()))
                .filter(config -> config.getSpec() == SPEC)
                .forEach(ModConfig::save);
    }
}
