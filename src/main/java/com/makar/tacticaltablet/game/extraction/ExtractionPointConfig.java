package com.makar.tacticaltablet.game.extraction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.makar.tacticaltablet.core.TacticalTabletMod;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ExtractionPointConfig {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final String CONFIG_FILE = "tacticaltablet_extraction.json";

    public boolean enabled = true;
    public String displayName = "бизнес-точка";
    public int startDelayMinSeconds = 180;
    public int startDelayMaxSeconds = 420;
    public int expireAtMatchTimeSeconds = 600;
    public int centerOffsetRadius = 30;
    public int maxLocationAttempts = 40;
    public double captureRadius = 16.0D;
    public double captureHalfHeight = 24.0D;
    public int requiredCaptureSeconds = 45;
    public double progressDecayPerSecond = 0.5D;
    public double borderSafetyMargin = 5.0D;
    public int minAlivePlayers = 3;
    public int minEventY = 50;
    public int maxEventY = 130;
    public boolean blockedLiquids = true;
    @Deprecated
    public boolean blockedLeaves = false;
    public boolean particleEnabled = true;
    public double particleVisibleDistance = 80.0D;
    public int particleUpdateIntervalTicks = 10;
    public int ringPoints = 56;
    public float particleScale = 0.7F;
    public int endingFadeSeconds = 12;
    public int winnerBossbarSeconds = 8;
    public int milestoneRewardIntervalSeconds = 15;
    public int milestoneClassXp = 10;
    public int milestoneCoins = 2;
    public int finalClassXp = 20;
    public int finalCoins = 15;
    public String teamRewardMode = "IN_ZONE_ONLY";
    public boolean navigatorEnabled = true;
    public String navigatorItem = "minecraft:recovery_compass";
    public boolean debugCommandsEnabled = true;

    public static ExtractionPointConfig load(MinecraftServer server) {
        ExtractionPointConfig defaults = normalize(new ExtractionPointConfig());
        if (server == null) return defaults;

        Path configPath = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().resolve(CONFIG_FILE);
        if (!Files.exists(configPath)) {
            writeDefaultConfig(configPath, defaults);
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            return normalize(GSON.fromJson(reader, ExtractionPointConfig.class));
        } catch (IOException | JsonSyntaxException exception) {
            TacticalTabletMod.LOGGER.error(
                    "Failed to read Tactical Tablet extraction config at {}. Falling back to defaults.",
                    configPath,
                    exception
            );
            return defaults;
        }
    }

    private static ExtractionPointConfig normalize(ExtractionPointConfig config) {
        ExtractionPointConfig defaults = new ExtractionPointConfig();
        if (config == null) return defaults;

        if (config.displayName == null || config.displayName.isBlank() || "Точка эвакуации".equals(config.displayName)) {
            config.displayName = defaults.displayName;
        }
        config.startDelayMinSeconds = Math.max(0, config.startDelayMinSeconds);
        config.startDelayMaxSeconds = Math.max(config.startDelayMinSeconds, config.startDelayMaxSeconds);
        config.expireAtMatchTimeSeconds = Math.max(1, config.expireAtMatchTimeSeconds);
        config.centerOffsetRadius = Math.max(0, config.centerOffsetRadius);
        config.maxLocationAttempts = Math.max(1, config.maxLocationAttempts);
        config.captureRadius = Math.max(1.0D, config.captureRadius);
        config.captureHalfHeight = Math.max(24.0D, config.captureHalfHeight);
        config.requiredCaptureSeconds = Math.max(1, config.requiredCaptureSeconds);
        config.progressDecayPerSecond = Math.max(0.0D, config.progressDecayPerSecond);
        config.borderSafetyMargin = Math.max(0.0D, config.borderSafetyMargin);
        config.minAlivePlayers = Math.max(1, config.minAlivePlayers);
        if (config.maxEventY < config.minEventY) {
            int temp = config.minEventY;
            config.minEventY = config.maxEventY;
            config.maxEventY = temp;
        }
        config.particleVisibleDistance = Math.max(1.0D, config.particleVisibleDistance);
        config.particleUpdateIntervalTicks = Math.max(1, config.particleUpdateIntervalTicks);
        config.ringPoints = Math.max(8, config.ringPoints);
        config.particleScale = Math.max(0.1F, config.particleScale);
        config.endingFadeSeconds = Math.max(1, config.endingFadeSeconds);
        config.winnerBossbarSeconds = Math.max(1, config.winnerBossbarSeconds);
        config.milestoneRewardIntervalSeconds = Math.max(1, config.milestoneRewardIntervalSeconds);
        config.milestoneClassXp = Math.max(0, config.milestoneClassXp);
        config.milestoneCoins = Math.max(0, config.milestoneCoins);
        config.finalClassXp = Math.max(0, config.finalClassXp);
        config.finalCoins = Math.max(0, config.finalCoins);
        if (config.teamRewardMode == null || config.teamRewardMode.isBlank()) config.teamRewardMode = defaults.teamRewardMode;
        if (config.navigatorItem == null || config.navigatorItem.isBlank()) config.navigatorItem = defaults.navigatorItem;
        return config;
    }

    private static void writeDefaultConfig(Path configPath, ExtractionPointConfig defaults) {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(defaults, writer);
            }
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to write default Tactical Tablet extraction config at {}", configPath, exception);
        }
    }
}
