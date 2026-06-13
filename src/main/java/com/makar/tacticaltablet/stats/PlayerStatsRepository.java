package com.makar.tacticaltablet.stats;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.integration.discord.DiscordConfig;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PlayerStatsRepository {

    private static final Gson GSON = new Gson();

    private PlayerStatsRepository() {
    }

    public static List<PlayerStats> loadAll(MinecraftServer server) {
        DiscordConfig config = DiscordConfig.get(server);
        return loadAll(config.getPlayersDirectoryPath());
    }

    public static List<PlayerStats> loadAll(Path playersDirectory) {
        List<PlayerStats> stats = new ArrayList<>();

        if (playersDirectory == null) {
            return stats;
        }

        try {
            Files.createDirectories(playersDirectory);
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to create Tactical Tablet players directory for Discord leaderboard: {}",
                    playersDirectory, exception);
            return stats;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(playersDirectory, "*.json")) {
            for (Path file : stream) {
                readFile(file).ifPresent(stats::add);
            }
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to read Tactical Tablet players directory for Discord leaderboard: {}",
                    playersDirectory, exception);
        }

        return stats;
    }

    private static java.util.Optional<PlayerStats> readFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);

            if (root == null) {
                return java.util.Optional.empty();
            }

            String fallbackName = fallbackName(file);
            String name = getString(root, "name", fallbackName);

            return java.util.Optional.of(new PlayerStats(
                    name,
                    getInt(root, "kills"),
                    getInt(root, "deaths"),
                    getInt(root, "wins"),
                    getInt(root, "matchesPlayed"),
                    getInt(root, "coins")
            ));
        } catch (JsonSyntaxException | IOException exception) {
            TacticalTabletMod.LOGGER.warn("Skipping corrupt Tactical Tablet player progress JSON: {}", file, exception);
            return java.util.Optional.empty();
        } catch (RuntimeException exception) {
            TacticalTabletMod.LOGGER.warn("Skipping unreadable Tactical Tablet player progress JSON: {}", file, exception);
            return java.util.Optional.empty();
        }
    }

    private static String getString(JsonObject root, String key, String fallback) {
        if (!root.has(key) || root.get(key).isJsonNull()) {
            return fallback;
        }

        try {
            String value = root.get(key).getAsString();
            return value == null || value.isBlank() ? fallback : value;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static int getInt(JsonObject root, String key) {
        if (!root.has(key) || root.get(key).isJsonNull()) {
            return 0;
        }

        try {
            return Math.max(0, root.get(key).getAsInt());
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private static String fallbackName(Path file) {
        String fileName = file == null || file.getFileName() == null ? "unknown" : file.getFileName().toString();

        if (fileName.endsWith(".json")) {
            return fileName.substring(0, fileName.length() - ".json".length());
        }

        return fileName;
    }
}

