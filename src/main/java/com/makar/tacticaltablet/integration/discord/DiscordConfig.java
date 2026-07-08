package com.makar.tacticaltablet.integration.discord;

import com.makar.tacticaltablet.core.TacticalTabletMod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DiscordConfig {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final String CONFIG_FILE = "tacticaltablet_discord.json";

    private static DiscordConfig current;
    private static Path configPath;

    private String webhookUrl = "";
    private String clanWarWebhookUrl = "";
    private String serverName = "DeluxeWarfare";
    private String playersDirectory = "";
    private int dailyHour = 20;
    private int dailyMinute = 0;
    private int topLimit = 10;

    private DiscordConfig() {
    }

    public static synchronized DiscordConfig get(MinecraftServer server) {
        if (current == null) {
            reload(server);
        }

        return current;
    }

    public static synchronized DiscordConfig reload(MinecraftServer server) {
        Path serverRoot = resolveServerRoot(server);
        Path configRoot = serverRoot.resolve("config");
        configPath = configRoot.resolve(CONFIG_FILE);

        DiscordConfig config = null;

        try {
            Files.createDirectories(configRoot);

            if (Files.exists(configPath)) {
                try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                    config = GSON.fromJson(reader, DiscordConfig.class);
                }
            }

            if (config == null) {
                config = new DiscordConfig();
                config.playersDirectory = defaultPlayersDirectory(serverRoot).toString();
                write(configPath, config);
            } else {
                boolean changed = config.normalize(serverRoot);
                if (changed) {
                    write(configPath, config);
                }
            }
        } catch (JsonSyntaxException exception) {
            TacticalTabletMod.LOGGER.error("Discord config is invalid: {}", configPath, exception);
            config = new DiscordConfig();
            config.playersDirectory = defaultPlayersDirectory(serverRoot).toString();
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to load Discord config: {}", configPath, exception);
            config = new DiscordConfig();
            config.playersDirectory = defaultPlayersDirectory(serverRoot).toString();
        }

        config.normalize(serverRoot);
        current = config;
        return current;
    }

    public String getWebhookUrl() {
        return webhookUrl == null ? "" : webhookUrl.trim();
    }

    public String getClanWarWebhookUrl() {
        return clanWarWebhookUrl == null ? "" : clanWarWebhookUrl.trim();
    }

    public String getServerName() {
        String value = serverName == null ? "" : serverName.trim();
        return value.isBlank() ? "DeluxeWarfare" : value;
    }

    public Path getPlayersDirectoryPath() {
        String value = playersDirectory == null ? "" : playersDirectory.trim();

        if (value.isBlank()) {
            return Path.of("tacticaltablet_data", "players").toAbsolutePath().normalize();
        }

        return Path.of(value).toAbsolutePath().normalize();
    }

    public int getDailyHour() {
        return dailyHour;
    }

    public int getDailyMinute() {
        return dailyMinute;
    }

    public int getTopLimit() {
        return topLimit;
    }

    public boolean hasWebhook() {
        return !getWebhookUrl().isBlank();
    }

    public boolean hasClanWarWebhook() {
        return !getClanWarWebhookUrl().isBlank();
    }

    private boolean normalize(Path serverRoot) {
        boolean changed = false;

        if (webhookUrl == null) {
            webhookUrl = "";
            changed = true;
        }

        if (clanWarWebhookUrl == null) {
            clanWarWebhookUrl = "";
            changed = true;
        }

        if (serverName == null || serverName.trim().isBlank()) {
            serverName = "DeluxeWarfare";
            changed = true;
        }

        if (playersDirectory == null || playersDirectory.trim().isBlank()) {
            playersDirectory = defaultPlayersDirectory(serverRoot).toString();
            changed = true;
        }

        int normalizedHour = Math.max(0, Math.min(23, dailyHour));
        if (normalizedHour != dailyHour) {
            dailyHour = normalizedHour;
            changed = true;
        }

        int normalizedMinute = Math.max(0, Math.min(59, dailyMinute));
        if (normalizedMinute != dailyMinute) {
            dailyMinute = normalizedMinute;
            changed = true;
        }

        int normalizedTopLimit = Math.max(1, Math.min(50, topLimit));
        if (normalizedTopLimit != topLimit) {
            topLimit = normalizedTopLimit;
            changed = true;
        }

        return changed;
    }

    private static void write(Path file, DiscordConfig config) throws IOException {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        }
    }

    private static Path resolveServerRoot(MinecraftServer server) {
        if (server == null) {
            return Path.of(".").toAbsolutePath().normalize();
        }

        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path serverRoot = worldRoot.getParent();

        return serverRoot == null ? worldRoot : serverRoot;
    }

    private static Path defaultPlayersDirectory(Path serverRoot) {
        return serverRoot
                .resolve("tacticaltablet_data")
                .resolve("players")
                .toAbsolutePath()
                .normalize();
    }
}

