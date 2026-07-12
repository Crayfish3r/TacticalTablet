package com.makar.tacticaltablet.game.zone;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.game.respawn.RespawnControlManager;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

public final class ZoneManager {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final String CONFIG_FILE = "tacticaltablet_zone.json";
    private static final double DEFAULT_CENTER_X = 0.0D;
    private static final double DEFAULT_CENTER_Z = 0.0D;
    private static final int DEFAULT_RANDOM_RADIUS = 100;
    private static final int MAX_RANDOM_RADIUS = 10000;
    private static final int SMALL_MATCH_MAX_PLAYERS = ZonePacingPolicy.SMALL_MATCH_MAX_PLAYERS;
    private static final double SMALL_MATCH_INITIAL_ZONE_SIZE = ZonePacingPolicy.SMALL_MATCH_INITIAL_ZONE_SIZE;
    private static final int FINAL_REVEAL_INTERVAL_SECONDS = ZonePacingPolicy.FINAL_REVEAL_INTERVAL_SECONDS;
    private static final int FINAL_REVEAL_DURATION_TICKS = ZonePacingPolicy.FINAL_REVEAL_DURATION_TICKS;
    private static final Random RANDOM = new Random();
    private static final int BOSS_BAR_VISIBLE_SECONDS = 8;
    private static final Phase[] PHASES = new Phase[]{
            new Phase(1, 360.0D, 1, 180, false, "Фаза 1: зона 360 блоков.", "Фаза 1 - зона 360 блоков"),
            new Phase(2, 260.0D, 150, 210, false, "Фаза 2: зона сужается до 260 блоков.", "Фаза 2 - зона сужается до 260"),
            new Phase(3, 180.0D, 150, 210, false, "Фаза 3: зона сужается до 180 блоков.", "Фаза 3 - зона сужается до 180"),
            new Phase(4, 110.0D, 120, 180, false, "Фаза 4: зона сужается до 110 блоков.", "Фаза 4 - зона сужается до 110"),
            new Phase(5, 50.0D, 90, 150, true, "Фаза 5: возрождения отключены, зона сужается до 50 блоков.", "Фаза 5 - зона сужается до 50"),
            new Phase(6, 1.0D, 90, Integer.MAX_VALUE, true, "Финальная зона: граница сужается до 1 блока.", "Финальная зона - сужение до 1 блока")
    };
    private static final int SMALL_MATCH_INITIAL_PHASE_INDEX = findPhaseIndexBySize(SMALL_MATCH_INITIAL_ZONE_SIZE);
    private static final int FINAL_REVEAL_START_PHASE_INDEX = PHASES.length - 2;

    private static int currentPhaseIndex = -1;
    private static int secondsLeft = 0;
    private static int bossBarSecondsLeft = 0;
    private static final ZoneFinalRevealScheduler FINAL_REVEAL_SCHEDULER =
            new ZoneFinalRevealScheduler(FINAL_REVEAL_INTERVAL_SECONDS);
    private static ServerBossEvent zoneBossBar;

    private ZoneManager() {
    }

    public static void start(MinecraftServer server) {
        currentPhaseIndex = -1;
        secondsLeft = 0;
        FINAL_REVEAL_SCHEDULER.reset();
        ZoneSettings settings = loadSettings(server);
        applyConfiguredCenter(server, settings, true);
        int playerCount = GameStateManager.onlinePlayers(server);
        int initialPhaseIndex = ZonePacingPolicy.initialPhaseIndex(playerCount, SMALL_MATCH_INITIAL_PHASE_INDEX);
        startAtPhase(server, initialPhaseIndex);
        TacticalTabletMod.LOGGER.info(
                "Tactical Tablet zone pacing selected: players={}, smallMatchMaxPlayers={}, initialPhase={}, initialSize={}",
                playerCount,
                SMALL_MATCH_MAX_PLAYERS,
                PHASES[initialPhaseIndex].number,
                PHASES[initialPhaseIndex].size
        );
    }

    public static void reset(MinecraftServer server) {
        currentPhaseIndex = -1;
        secondsLeft = 0;
        bossBarSecondsLeft = 0;
        FINAL_REVEAL_SCHEDULER.reset();
        hideBossBar();

        ServerLevel overworld = GameStateManager.getOverworld(server);
        if (overworld == null) return;

        ZoneSettings settings = loadSettings(server);
        WorldBorder border = overworld.getWorldBorder();
        border.setCenter(settings.zoneCenterX, settings.zoneCenterZ);
        border.setSize(360.0D);
        border.setDamageSafeZone(0.0D);
        border.setDamagePerBlock(2.0D);
        border.setWarningBlocks(8);
        border.setWarningTime(15);
    }

    public static void tick(MinecraftServer server) {
        if (server == null || currentPhaseIndex < 0) return;
        syncBossBarPlayers(server);
        tickBossBar();
        tickFinalReveal(server);

        if (secondsLeft == Integer.MAX_VALUE) return;

        if (secondsLeft > 0) {
            secondsLeft--;
            return;
        }

        int nextPhase = currentPhaseIndex + 1;
        if (nextPhase < PHASES.length) {
            applyPhase(server, nextPhase);
        }
    }

    private static void applyPhase(MinecraftServer server, int phaseIndex) {
        applyPhase(server, phaseIndex, false);
    }

    private static void startAtPhase(MinecraftServer server, int phaseIndex) {
        applyPhase(server, phaseIndex, true);
    }

    private static void applyPhase(MinecraftServer server, int phaseIndex, boolean forceImmediate) {
        ServerLevel overworld = GameStateManager.getOverworld(server);
        if (overworld == null || phaseIndex < 0 || phaseIndex >= PHASES.length) return;

        Phase phase = PHASES[phaseIndex];
        WorldBorder border = overworld.getWorldBorder();
        double currentSize = border.getSize();

        border.setDamageSafeZone(0.0D);
        border.setDamagePerBlock(2.0D);
        border.setWarningBlocks(8);
        border.setWarningTime(15);

        if (forceImmediate || phase.transitionSeconds <= 1) {
            border.setSize(phase.size);
        } else {
            border.lerpSizeBetween(currentSize, phase.size, phase.transitionSeconds * 1000L);
        }

        currentPhaseIndex = phaseIndex;
        secondsLeft = phase.durationSeconds;

        if (phase.disableRespawns) {
            RespawnControlManager.disableRespawns(server);
        }

        showBossBar(server, phase);
        broadcast(server, "[WAR] " + phase.message);
        TacticalTabletMod.LOGGER.info(
                "Tactical Tablet zone phase {} started: targetSize={}, transition={}s, duration={}s",
                phase.number,
                phase.size,
                phase.transitionSeconds,
                phase.durationSeconds
        );
    }

    private static void tickFinalReveal(MinecraftServer server) {
        if (!FINAL_REVEAL_SCHEDULER.tick(currentPhaseIndex, FINAL_REVEAL_START_PHASE_INDEX, PHASES.length)) {
            return;
        }

        int affected = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isFinalRevealTarget(player)) continue;

            player.addEffect(new MobEffectInstance(
                    MobEffects.GLOWING,
                    FINAL_REVEAL_DURATION_TICKS,
                    0,
                    false,
                    false,
                    true
            ));
            affected++;
        }

        TacticalTabletMod.LOGGER.info("Tactical Tablet final zone reveal applied to {} player(s)", affected);
    }

    private static boolean isFinalRevealTarget(ServerPlayer player) {
        return player != null
                && ZonePacingPolicy.isFinalRevealTarget(
                        LivesManager.isAliveParticipant(player),
                        player.isSpectator(),
                        player.getTags().contains("war.playing")
                );
    }

    private static void showBossBar(MinecraftServer server, Phase phase) {
        MutableComponent title = Component.literal(phase.bossBarText);

        if (zoneBossBar == null) {
            zoneBossBar = new ServerBossEvent(
                    title,
                    BossEvent.BossBarColor.YELLOW,
                    BossEvent.BossBarOverlay.PROGRESS
            );
        } else {
            zoneBossBar.setName(title);
            zoneBossBar.setColor(phase.number >= 5 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW);
        }

        zoneBossBar.setProgress(1.0F);
        zoneBossBar.setVisible(true);
        bossBarSecondsLeft = BOSS_BAR_VISIBLE_SECONDS;
        syncBossBarPlayers(server);
    }

    private static void syncBossBarPlayers(MinecraftServer server) {
        if (zoneBossBar == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            zoneBossBar.addPlayer(player);
        }
    }

    private static void tickBossBar() {
        if (zoneBossBar == null || !zoneBossBar.isVisible()) return;

        if (bossBarSecondsLeft <= 0) {
            zoneBossBar.setVisible(false);
            zoneBossBar.removeAllPlayers();
            return;
        }

        bossBarSecondsLeft--;
        float progress = Math.max(0.0F, Math.min(1.0F, bossBarSecondsLeft / (float) BOSS_BAR_VISIBLE_SECONDS));
        zoneBossBar.setProgress(progress);
    }

    private static void hideBossBar() {
        if (zoneBossBar == null) return;

        bossBarSecondsLeft = 0;
        zoneBossBar.removeAllPlayers();
        zoneBossBar.setVisible(false);
    }

    private static void applyConfiguredCenter(MinecraftServer server, ZoneSettings settings, boolean randomize) {
        ServerLevel overworld = GameStateManager.getOverworld(server);
        if (overworld == null) return;

        double x = settings.zoneCenterX;
        double z = settings.zoneCenterZ;

        if (randomize && settings.zoneRandomRadius > 0) {
            x += RANDOM.nextInt(settings.zoneRandomRadius * 2 + 1) - settings.zoneRandomRadius;
            z += RANDOM.nextInt(settings.zoneRandomRadius * 2 + 1) - settings.zoneRandomRadius;
        }

        WorldBorder border = overworld.getWorldBorder();
        border.setCenter(x, z);
        TacticalTabletMod.LOGGER.info(
                "Tactical Tablet zone center selected by map config: baseX={}, baseZ={}, radius={}, finalX={}, finalZ={}",
                settings.zoneCenterX,
                settings.zoneCenterZ,
                settings.zoneRandomRadius,
                border.getCenterX(),
                border.getCenterZ()
        );
    }

    private static ZoneSettings loadSettings(MinecraftServer server) {
        ZoneSettings defaults = ZoneSettings.defaults();
        if (server == null) return defaults;

        Path configPath = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().resolve(CONFIG_FILE);

        if (!Files.exists(configPath)) {
            writeDefaultConfig(configPath, defaults);
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            ZoneSettings loaded = GSON.fromJson(reader, ZoneSettings.class);
            return normalize(loaded);
        } catch (IOException | JsonSyntaxException exception) {
            TacticalTabletMod.LOGGER.error(
                    "Failed to read Tactical Tablet zone config at {}. Falling back to default center.",
                    configPath,
                    exception
            );
            return defaults;
        }
    }

    private static ZoneSettings normalize(ZoneSettings value) {
        ZoneSettings defaults = ZoneSettings.defaults();
        if (value == null) return defaults;

        if (value.zoneCenterX == null) {
            value.zoneCenterX = defaults.zoneCenterX;
        }

        if (value.zoneCenterZ == null) {
            value.zoneCenterZ = defaults.zoneCenterZ;
        }

        if (value.zoneRandomRadius == null) {
            value.zoneRandomRadius = defaults.zoneRandomRadius;
        }

        value.zoneRandomRadius = Math.max(0, Math.min(MAX_RANDOM_RADIUS, value.zoneRandomRadius));
        return value;
    }

    private static void writeDefaultConfig(Path configPath, ZoneSettings defaults) {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(defaults, writer);
            }
            TacticalTabletMod.LOGGER.info("Created default Tactical Tablet zone config at {}", configPath);
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.warn("Could not create default Tactical Tablet zone config at {}", configPath, exception);
        }
    }

    private static void broadcast(MinecraftServer server, String message) {
        Component component = Component.literal(message);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(component);
        }
    }

    private static int findPhaseIndexBySize(double size) {
        for (int i = 0; i < PHASES.length; i++) {
            if (Double.compare(PHASES[i].size, size) == 0) {
                return i;
            }
        }

        throw new IllegalStateException("Zone phase with size " + size + " is not configured");
    }

    private record Phase(
            int number,
            double size,
            int transitionSeconds,
            int durationSeconds,
            boolean disableRespawns,
            String message,
            String bossBarText
    ) {
    }

    private static final class ZoneSettings {
        Double zoneCenterX;
        Double zoneCenterZ;
        Integer zoneRandomRadius;

        private static ZoneSettings defaults() {
            ZoneSettings settings = new ZoneSettings();
            settings.zoneCenterX = DEFAULT_CENTER_X;
            settings.zoneCenterZ = DEFAULT_CENTER_Z;
            settings.zoneRandomRadius = DEFAULT_RANDOM_RADIUS;
            return settings;
        }
    }
}
