package com.makar.tacticaltablet.prefix;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.tablet.net.PacketHandler;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PrefixManager {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final String DATA_DIRECTORY = "tacticaltabletdata";
    private static final String PREFIXES_FILE = "prefixes.json";
    private static final DateTimeFormatter BROKEN_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final Map<UUID, PrefixPlayerData> players = new HashMap<>();
    private static Path prefixesFile;
    private static MinecraftServer loadedServer;
    private static boolean loaded;

    private PrefixManager() {
    }

    public static synchronized void load(MinecraftServer server) {
        if (server == null) return;

        initPath(server);
        players.clear();

        try {
            Files.createDirectories(prefixesFile.getParent());
            if (!Files.exists(prefixesFile)) {
                saveAtomic();
                loaded = true;
                loadedServer = server;
                return;
            }

            try (Reader reader = Files.newBufferedReader(prefixesFile, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                readPlayers(root);
            }
            loaded = true;
            loadedServer = server;
        } catch (JsonSyntaxException | IllegalStateException | IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to load Tactical Tablet prefixes from {}", prefixesFile, exception);
            backupBrokenFile();
            players.clear();
            loaded = true;
            loadedServer = server;
            save();
        }
    }

    public static synchronized void save() {
        try {
            saveAtomic();
        } catch (RuntimeException exception) {
            TacticalTabletMod.LOGGER.error("Failed to save Tactical Tablet prefixes to {}", prefixesFile, exception);
        }
    }

    public static synchronized void saveAtomic() {
        if (prefixesFile == null) return;

        Path tmp = prefixesFile.resolveSibling(PREFIXES_FILE + ".tmp");
        try {
            Files.createDirectories(prefixesFile.getParent());
            JsonObject root = buildStorageJson();
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            moveReplace(tmp, prefixesFile);
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to save Tactical Tablet prefixes to {}", prefixesFile, exception);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
        }
    }

    public static synchronized void clearRuntime() {
        players.clear();
        prefixesFile = null;
        loadedServer = null;
        loaded = false;
    }

    public static synchronized PrefixRole getRole(UUID uuid) {
        PrefixPlayerData data = getData(uuid);
        return data == null ? PrefixRole.NONE : data.effectiveRole();
    }

    public static synchronized PrefixRole getRole(ServerPlayer player) {
        return player == null ? PrefixRole.NONE : getRole(player.getUUID());
    }

    public static synchronized PrefixPlayerData getData(UUID uuid) {
        if (uuid == null) return null;
        return players.get(uuid);
    }

    /**
     * Role permission API for future systems. It only checks assigned prefix-role grants;
     * gameplay systems must explicitly call this method before applying any effect.
     */
    public static synchronized boolean hasPermission(ServerPlayer player, String permission) {
        if (player == null || permission == null || permission.isBlank()) return false;
        return getRole(player).hasPermission(permission);
    }

    public static synchronized boolean hasPermission(UUID uuid, String permission) {
        if (uuid == null || permission == null || permission.isBlank()) return false;
        return getRole(uuid).hasPermission(permission);
    }

    public static synchronized boolean hasRoleAtLeast(ServerPlayer player, PrefixRole role) {
        if (player == null) return false;
        return getRole(player).isAtLeast(role);
    }

    public static synchronized boolean hasRoleAtLeast(UUID uuid, PrefixRole role) {
        if (uuid == null) return false;
        return getRole(uuid).isAtLeast(role);
    }

    public static synchronized void setRole(ServerPlayer target, PrefixRole role, long expiresAt) {
        if (target == null) return;
        if (!loaded) {
            load(target.server);
        }
        setRole(target.getUUID(), target.getGameProfile().getName(), role, expiresAt);
    }

    public static synchronized void setRole(UUID uuid, String lastKnownName, PrefixRole role, long expiresAt) {
        if (uuid == null) return;
        PrefixRole safeRole = role == null ? PrefixRole.NONE : role;
        if (safeRole == PrefixRole.NONE) {
            clearRole(uuid);
            return;
        }

        players.put(uuid, new PrefixPlayerData(
                uuid,
                lastKnownName == null ? "" : lastKnownName,
                safeRole,
                Math.max(0L, expiresAt)
        ));
        save();
    }

    public static synchronized void clearRole(UUID uuid) {
        if (uuid == null) return;
        players.remove(uuid);
        save();
    }

    public static synchronized void updateLastKnownName(UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank()) return;

        PrefixPlayerData data = players.get(uuid);
        if (data == null || name.equals(data.lastKnownName())) return;

        players.put(uuid, data.withLastKnownName(name));
        save();
    }

    public static synchronized void cleanupExpired() {
        boolean changed = false;
        Iterator<Map.Entry<UUID, PrefixPlayerData>> iterator = players.entrySet().iterator();
        while (iterator.hasNext()) {
            PrefixPlayerData data = iterator.next().getValue();
            if (data != null && data.expired()) {
                iterator.remove();
                changed = true;
            }
        }
        if (changed) {
            save();
            if (loadedServer != null) {
                syncAll(loadedServer);
                updateTabNames(loadedServer);
            }
        }
    }

    public static synchronized void sync(ServerPlayer viewer) {
        if (viewer == null) return;
        PacketHandler.sendToPlayer(viewer, new PrefixListPacket(buildOnlineEntries(viewer.server)));
    }

    public static synchronized void syncAll(MinecraftServer server) {
        if (server == null) return;
        List<PrefixListPacket.Entry> entries = buildOnlineEntries(server);
        PrefixListPacket packet = new PrefixListPacket(entries);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketHandler.sendToPlayer(player, packet);
        }
    }

    public static synchronized void updateTabNames(MinecraftServer server) {
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.refreshTabListName();
        }
    }

    public static synchronized Component buildDisplayName(ServerPlayer player) {
        if (player == null) return Component.empty();
        return PrefixDisplayHelper.appendSuffix(player.getDisplayName(), getRole(player));
    }

    public static synchronized Component buildChatName(ServerPlayer player) {
        if (player == null) return Component.empty();
        return PrefixDisplayHelper.appendSuffix(player.getDisplayName(), getRole(player));
    }

    public static synchronized List<PrefixPlayerData> getAssignedPlayers() {
        cleanupExpired();
        return players.values().stream()
                .filter(data -> data != null && data.effectiveRole().visible())
                .sorted(Comparator
                        .comparing((PrefixPlayerData data) -> data.effectiveRole().priority()).reversed()
                        .thenComparing(data -> data.lastKnownName().toLowerCase(Locale.ROOT)))
                .toList();
    }

    public static synchronized String getKnownName(UUID uuid) {
        PrefixPlayerData data = uuid == null ? null : players.get(uuid);
        return data == null ? "" : data.lastKnownName();
    }

    public static synchronized Optional<KnownPlayer> findKnownPlayerByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();

        String normalized = name.trim().toLowerCase(Locale.ROOT);
        return players.values().stream()
                .filter(data -> data != null && data.lastKnownName() != null)
                .filter(data -> data.lastKnownName().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst()
                .map(data -> new KnownPlayer(data.uuid(), data.lastKnownName()));
    }

    private static List<PrefixListPacket.Entry> buildOnlineEntries(MinecraftServer server) {
        List<PrefixListPacket.Entry> entries = new ArrayList<>();
        if (server == null) return entries;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PrefixRole role = getRole(player);
            entries.add(new PrefixListPacket.Entry(
                    player.getUUID(),
                    player.getGameProfile().getName(),
                    role.id(),
                    role.displayName(),
                    role.textColor(),
                    role.backgroundColor(),
                    role.priority()
            ));
            if (entries.size() >= PrefixListPacket.MAX_PLAYERS) {
                break;
            }
        }
        return entries;
    }

    private static void readPlayers(JsonObject root) {
        if (root == null || !root.has("players") || !root.get("players").isJsonObject()) {
            return;
        }

        JsonObject object = root.getAsJsonObject("players");
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            UUID uuid = parseUuid(entry.getKey());
            if (uuid == null || !entry.getValue().isJsonObject()) continue;

            JsonObject playerObject = entry.getValue().getAsJsonObject();
            String lastKnownName = getString(playerObject, "lastKnownName", "");
            PrefixRole role = PrefixRole.byId(getString(playerObject, "role", PrefixRole.NONE.id()));
            long expiresAt = getLong(playerObject, "expiresAt", 0L);

            if (role == PrefixRole.NONE) continue;
            PrefixPlayerData data = new PrefixPlayerData(uuid, lastKnownName, role, Math.max(0L, expiresAt));
            if (!data.expired()) {
                players.put(uuid, data);
            }
        }
    }

    private static JsonObject buildStorageJson() {
        JsonObject root = new JsonObject();
        JsonObject playersObject = new JsonObject();

        players.values().stream()
                .filter(data -> data != null && data.role() != null && data.role() != PrefixRole.NONE)
                .sorted(Comparator.comparing(data -> data.uuid().toString()))
                .forEach(data -> {
                    JsonObject playerObject = new JsonObject();
                    playerObject.addProperty("lastKnownName", data.lastKnownName() == null ? "" : data.lastKnownName());
                    playerObject.addProperty("role", data.role().id());
                    playerObject.addProperty("expiresAt", Math.max(0L, data.expiresAt()));
                    playersObject.add(data.uuid().toString(), playerObject);
                });

        root.add("players", playersObject);
        return root;
    }

    private static void initPath(MinecraftServer server) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path serverRoot = worldRoot.getParent();
        if (serverRoot == null) {
            serverRoot = worldRoot;
        }

        prefixesFile = serverRoot.resolve(DATA_DIRECTORY).resolve(PREFIXES_FILE);
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void backupBrokenFile() {
        if (prefixesFile == null || !Files.exists(prefixesFile)) return;

        String timestamp = BROKEN_TIMESTAMP.format(LocalDateTime.now());
        Path broken = prefixesFile.resolveSibling("prefixes.broken." + timestamp + ".json");
        try {
            Files.move(prefixesFile, broken, StandardCopyOption.REPLACE_EXISTING);
            TacticalTabletMod.LOGGER.warn("Moved broken Tactical Tablet prefixes file to {}", broken);
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to move broken Tactical Tablet prefixes file {}", prefixesFile, exception);
        }
    }

    private static String getString(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
        try {
            return object.get(key).getAsString();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static long getLong(JsonObject object, String key, long fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
        try {
            return object.get(key).getAsLong();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public record KnownPlayer(UUID uuid, String name) {
    }
}
