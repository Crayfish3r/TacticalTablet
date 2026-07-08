package com.makar.tacticaltablet.progression.kit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.map.MapRotationManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class KitRotationManager {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final int DATA_VERSION = 1;
    private static final String STATE_FILE = "kit_rotation_state.json";

    private static Path statePath;
    private static RotationState state = new RotationState();
    private static volatile boolean altKitsActive;

    private KitRotationManager() {
    }

    public static synchronized void onServerStarted(MinecraftServer server) {
        try {
            initStorage(server);
            updateForCurrentMap(server);
            altKitsActive = state.altKitsActive;

            TacticalTabletMod.LOGGER.info(
                    "Tactical Tablet kit rotation initialized. altKitsActive={}, currentMap={}, lastRotation={}",
                    state.altKitsActive,
                    state.currentMap,
                    state.lastRotation
            );
        } catch (RuntimeException exception) {
            altKitsActive = false;
            TacticalTabletMod.LOGGER.error("Failed to initialize Tactical Tablet kit rotation. Regular kits will be used.", exception);
        }
    }

    public static boolean isAltKitsActive() {
        return altKitsActive;
    }

    public static synchronized void resetRuntime() {
        statePath = null;
        state = new RotationState();
        altKitsActive = false;
    }

    private static void updateForCurrentMap(MinecraftServer server) {
        MapRotationManager.RotationStatus rotationStatus = MapRotationManager.getStatus(server);
        String currentMap = currentMapName(server, rotationStatus);
        String lastRotation = nullToEmpty(rotationStatus.lastRotation()).trim();
        String mapIdentity = mapIdentity(currentMap, lastRotation);

        if (state.mapIdentity.isBlank()) {
            state.mapIdentity = mapIdentity;
            state.currentMap = currentMap;
            state.lastRotation = lastRotation;
            state.altKitsActive = false;
            saveState();
            return;
        }

        if (!state.mapIdentity.equals(mapIdentity)) {
            state.altKitsActive = !state.altKitsActive;
            state.mapIdentity = mapIdentity;
        }

        state.currentMap = currentMap;
        state.lastRotation = lastRotation;
        saveState();
    }

    private static void initStorage(MinecraftServer server) {
        if (server == null) {
            throw new IllegalStateException("MinecraftServer is required for kit rotation.");
        }

        if (statePath != null) {
            return;
        }

        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path serverRoot = worldRoot.getParent() == null ? worldRoot : worldRoot.getParent();
        Path dataRoot = serverRoot.resolve("tacticaltablet_data");
        statePath = dataRoot.resolve(STATE_FILE);

        try {
            Files.createDirectories(dataRoot);
            state = readOrCreateState();
            normalizeState();
            saveState();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize kit rotation state at " + statePath, exception);
        }
    }

    private static RotationState readOrCreateState() throws IOException {
        if (!Files.exists(statePath)) {
            return new RotationState();
        }

        try (Reader reader = Files.newBufferedReader(statePath, StandardCharsets.UTF_8)) {
            RotationState loaded = GSON.fromJson(reader, RotationState.class);
            return loaded == null ? new RotationState() : loaded;
        } catch (JsonSyntaxException exception) {
            Path broken = statePath.resolveSibling(STATE_FILE + ".broken-" + System.currentTimeMillis());
            Files.move(statePath, broken, StandardCopyOption.REPLACE_EXISTING);
            TacticalTabletMod.LOGGER.error("Broken kit rotation state moved to {}", broken, exception);
            return new RotationState();
        }
    }

    private static void saveState() {
        if (statePath == null) return;
        normalizeState();

        Path temp = statePath.resolveSibling(statePath.getFileName() + ".tmp");
        try {
            Files.createDirectories(statePath.getParent());
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(state, writer);
            }

            try {
                Files.move(temp, statePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, statePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to save kit rotation state at {}", statePath, exception);
        }
    }

    private static void normalizeState() {
        state.dataVersion = DATA_VERSION;
        state.mapIdentity = nullToEmpty(state.mapIdentity).trim();
        state.currentMap = nullToEmpty(state.currentMap).trim();
        state.lastRotation = nullToEmpty(state.lastRotation).trim();
    }

    private static String currentMapName(MinecraftServer server, MapRotationManager.RotationStatus rotationStatus) {
        String currentMap = rotationStatus.currentMap();
        if (currentMap != null && !currentMap.isBlank()) {
            return currentMap.trim();
        }

        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        return worldRoot.getFileName() == null ? "" : worldRoot.getFileName().toString();
    }

    private static String mapIdentity(String currentMap, String lastRotation) {
        if (lastRotation != null && !lastRotation.isBlank()) {
            return "rotation:" + lastRotation.trim();
        }

        return "map:" + nullToEmpty(currentMap).trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class RotationState {
        int dataVersion = DATA_VERSION;
        String mapIdentity = "";
        String currentMap = "";
        String lastRotation = "";
        boolean altKitsActive;
    }
}
