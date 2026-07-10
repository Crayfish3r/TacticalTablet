package com.makar.tacticaltablet.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.makar.tacticaltablet.core.TacticalTabletMod;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class PunishmentManager {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final String DATA_DIRECTORY = "tacticaltabletdata";
    private static final String PUNISHMENTS_FILE = "punishments.json";
    private static final DateTimeFormatter BROKEN_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private static final Map<UUID, PunishmentRecord> mutes = new HashMap<>();
    private static final Map<UUID, PunishmentRecord> tempBans = new HashMap<>();
    private static Path punishmentsFile;

    private PunishmentManager() {
    }

    public static synchronized void load(MinecraftServer server) {
        if (server == null) return;

        initPath(server);
        mutes.clear();
        tempBans.clear();

        try {
            Files.createDirectories(punishmentsFile.getParent());
            if (!Files.exists(punishmentsFile)) {
                saveAtomic();
                return;
            }

            try (Reader reader = Files.newBufferedReader(punishmentsFile, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                readRecords(root, "mutes", PunishmentType.MUTE, mutes);
                readRecords(root, "tempBans", PunishmentType.TEMPBAN, tempBans);
            }
            cleanupExpired();
        } catch (JsonSyntaxException | IllegalStateException | IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to load Tactical Tablet punishments from {}", punishmentsFile, exception);
            backupBrokenFile();
            mutes.clear();
            tempBans.clear();
            saveAtomic();
        }
    }

    public static synchronized void resetRuntime() {
        mutes.clear();
        tempBans.clear();
        punishmentsFile = null;
    }

    public static synchronized void mute(UUID target, String name, UUID issuer, String issuerName, long expiresAt, String reason) {
        if (target == null) return;
        mutes.put(target, new PunishmentRecord(
                PunishmentType.MUTE,
                target,
                name,
                issuer,
                issuerName,
                System.currentTimeMillis(),
                expiresAt,
                reason
        ));
        saveAtomic();
    }

    public static synchronized boolean unmute(UUID target) {
        if (target == null) return false;
        boolean removed = mutes.remove(target) != null;
        if (removed) {
            saveAtomic();
        }
        return removed;
    }

    public static synchronized void tempBan(UUID target, String name, UUID issuer, String issuerName, long expiresAt, String reason) {
        if (target == null) return;
        tempBans.put(target, new PunishmentRecord(
                PunishmentType.TEMPBAN,
                target,
                name,
                issuer,
                issuerName,
                System.currentTimeMillis(),
                expiresAt,
                reason
        ));
        saveAtomic();
    }

    public static synchronized boolean unban(UUID target) {
        if (target == null) return false;
        boolean removed = tempBans.remove(target) != null;
        if (removed) {
            saveAtomic();
        }
        return removed;
    }

    public static synchronized boolean isMuted(UUID target) {
        return getMute(target) != null;
    }

    public static synchronized boolean isTempBanned(UUID target) {
        return getTempBan(target) != null;
    }

    public static synchronized PunishmentRecord getMute(UUID target) {
        return getActive(target, mutes);
    }

    public static synchronized PunishmentRecord getTempBan(UUID target) {
        return getActive(target, tempBans);
    }

    public static synchronized void cleanupExpired() {
        boolean changed = removeExpired(mutes) | removeExpired(tempBans);
        if (changed) {
            saveAtomic();
        }
    }

    public static synchronized void saveAtomic() {
        if (punishmentsFile == null) return;

        Path tmp = punishmentsFile.resolveSibling(PUNISHMENTS_FILE + ".tmp");
        try {
            Files.createDirectories(punishmentsFile.getParent());
            JsonObject root = buildStorageJson();
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            moveReplace(tmp, punishmentsFile);
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to save Tactical Tablet punishments to {}", punishmentsFile, exception);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
        }
    }

    public static String formatExpiresAt(long expiresAt) {
        return expiresAt <= 0L ? "never" : DISPLAY_TIME.format(Instant.ofEpochMilli(expiresAt));
    }

    public static String formatRemaining(long expiresAt) {
        long remaining = Math.max(0L, expiresAt - System.currentTimeMillis());
        long seconds = remaining / 1000L;
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;

        if (days > 0L) return days + "d " + hours + "h";
        if (hours > 0L) return hours + "h " + minutes + "m";
        if (minutes > 0L) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    private static PunishmentRecord getActive(UUID target, Map<UUID, PunishmentRecord> records) {
        if (target == null) return null;
        PunishmentRecord record = records.get(target);
        if (record == null) return null;

        if (!record.expired()) {
            return record;
        }

        records.remove(target);
        saveAtomic();
        return null;
    }

    private static boolean removeExpired(Map<UUID, PunishmentRecord> records) {
        boolean changed = false;
        Iterator<Map.Entry<UUID, PunishmentRecord>> iterator = records.entrySet().iterator();
        while (iterator.hasNext()) {
            PunishmentRecord record = iterator.next().getValue();
            if (record == null || record.expired()) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    private static void readRecords(
            JsonObject root,
            String key,
            PunishmentType type,
            Map<UUID, PunishmentRecord> target
    ) {
        if (root == null || !root.has(key) || !root.get(key).isJsonObject()) {
            return;
        }

        JsonObject object = root.getAsJsonObject(key);
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            UUID uuid = parseUuid(entry.getKey());
            if (uuid == null || !entry.getValue().isJsonObject()) continue;

            JsonObject recordObject = entry.getValue().getAsJsonObject();
            PunishmentRecord record = new PunishmentRecord(
                    type,
                    uuid,
                    getString(recordObject, "targetName", ""),
                    parseUuid(getString(recordObject, "issuerUuid", "")),
                    getString(recordObject, "issuerName", ""),
                    getLong(recordObject, "createdAt", 0L),
                    getLong(recordObject, "expiresAt", 0L),
                    getString(recordObject, "reason", "")
            );
            if (!record.expired()) {
                target.put(uuid, record);
            }
        }
    }

    private static JsonObject buildStorageJson() {
        JsonObject root = new JsonObject();
        root.add("mutes", buildRecordObject(mutes));
        root.add("tempBans", buildRecordObject(tempBans));
        return root;
    }

    private static JsonObject buildRecordObject(Map<UUID, PunishmentRecord> records) {
        JsonObject object = new JsonObject();
        records.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    PunishmentRecord record = entry.getValue();
                    if (record == null || record.expired()) return;

                    JsonObject recordObject = new JsonObject();
                    recordObject.addProperty("targetName", record.targetName());
                    recordObject.addProperty("issuerUuid", record.issuerUuid() == null ? "" : record.issuerUuid().toString());
                    recordObject.addProperty("issuerName", record.issuerName());
                    recordObject.addProperty("createdAt", record.createdAt());
                    recordObject.addProperty("expiresAt", record.expiresAt());
                    recordObject.addProperty("reason", record.reason());
                    object.add(entry.getKey().toString(), recordObject);
                });
        return object;
    }

    private static void initPath(MinecraftServer server) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path serverRoot = worldRoot.getParent();
        if (serverRoot == null) {
            serverRoot = worldRoot;
        }

        punishmentsFile = serverRoot.resolve(DATA_DIRECTORY).resolve(PUNISHMENTS_FILE);
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void backupBrokenFile() {
        if (punishmentsFile == null || !Files.exists(punishmentsFile)) return;

        String timestamp = BROKEN_TIMESTAMP.format(LocalDateTime.now());
        Path broken = punishmentsFile.resolveSibling("punishments.broken." + timestamp + ".json");
        try {
            Files.move(punishmentsFile, broken, StandardCopyOption.REPLACE_EXISTING);
            TacticalTabletMod.LOGGER.warn("Moved broken Tactical Tablet punishments file to {}", broken);
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to move broken Tactical Tablet punishments file {}", punishmentsFile, exception);
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
}
