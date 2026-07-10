package com.makar.tacticaltablet.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.storage.AtomicFileStore;
import com.makar.tacticaltablet.storage.FileSaveResult;
import com.makar.tacticaltablet.storage.LegacyStorageMigration;
import com.makar.tacticaltablet.storage.TacticalTabletStoragePaths;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
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
    private static final String PUNISHMENTS_FILE = "punishments.json";
    private static final DateTimeFormatter BROKEN_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private static final Map<UUID, PunishmentRecord> mutes = new HashMap<>();
    private static final Map<UUID, PunishmentRecord> tempBans = new HashMap<>();
    private static final AtomicFileStore FILE_STORE = new AtomicFileStore();
    private static Path punishmentsFile;
    private static TacticalTabletStoragePaths storagePaths;

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
        }
    }

    public static synchronized void resetRuntime() {
        mutes.clear();
        tempBans.clear();
        punishmentsFile = null;
        storagePaths = null;
    }

    public static synchronized FileSaveResult mute(UUID target, String name, UUID issuer, String issuerName, long expiresAt, String reason) {
        if (target == null) return FileSaveResult.failure(uninitializedPath(), "Mute target is required", null);
        PunishmentRecord previous = mutes.put(target, new PunishmentRecord(
                PunishmentType.MUTE,
                target,
                name,
                issuer,
                issuerName,
                System.currentTimeMillis(),
                expiresAt,
                reason
        ));
        FileSaveResult result = saveAtomic();
        if (result.status() != FileSaveResult.Status.SUCCESS) {
            restoreRecord(mutes, target, previous);
        }
        return result;
    }

    public static synchronized RemovalResult unmute(UUID target) {
        return removeAndSave(target, mutes);
    }

    public static synchronized FileSaveResult tempBan(UUID target, String name, UUID issuer, String issuerName, long expiresAt, String reason) {
        if (target == null) return FileSaveResult.failure(uninitializedPath(), "Temp-ban target is required", null);
        PunishmentRecord previous = tempBans.put(target, new PunishmentRecord(
                PunishmentType.TEMPBAN,
                target,
                name,
                issuer,
                issuerName,
                System.currentTimeMillis(),
                expiresAt,
                reason
        ));
        FileSaveResult result = saveAtomic();
        if (result.status() != FileSaveResult.Status.SUCCESS) {
            restoreRecord(tempBans, target, previous);
        }
        return result;
    }

    public static synchronized RemovalResult unban(UUID target) {
        return removeAndSave(target, tempBans);
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

    public static synchronized FileSaveResult cleanupExpired() {
        Map<UUID, PunishmentRecord> previousMutes = new HashMap<>(mutes);
        Map<UUID, PunishmentRecord> previousTempBans = new HashMap<>(tempBans);
        boolean changed = removeExpired(mutes) | removeExpired(tempBans);
        if (changed) {
            FileSaveResult result = saveAtomic();
            if (result.status() != FileSaveResult.Status.SUCCESS) {
                mutes.clear();
                mutes.putAll(previousMutes);
                tempBans.clear();
                tempBans.putAll(previousTempBans);
            }
            return result;
        }
        return FileSaveResult.success(uninitializedPath());
    }

    public static synchronized FileSaveResult saveAtomic() {
        if (punishmentsFile == null) {
            return FileSaveResult.failure(uninitializedPath(), "Punishment storage path has not been initialized", null);
        }
        FileSaveResult result = FILE_STORE.write(punishmentsFile, writer -> GSON.toJson(buildStorageJson(), writer));
        if (result.status() != FileSaveResult.Status.SUCCESS) {
            TacticalTabletMod.LOGGER.error("Failed to save Tactical Tablet punishments to {}: {}",
                    result.target(), result.diagnostic(), result.exception().orElse(null));
        }
        return result;
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
        FileSaveResult result = saveAtomic();
        if (result.status() != FileSaveResult.Status.SUCCESS) {
            records.put(target, record);
        }
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
        storagePaths = TacticalTabletStoragePaths.fromServer(server);
        LegacyStorageMigration.Result migration = LegacyStorageMigration.migrate(
                storagePaths,
                message -> TacticalTabletMod.LOGGER.warn("{}", message)
        );
        if (migration.status() == LegacyStorageMigration.Status.FAILED) {
            TacticalTabletMod.LOGGER.error("Tactical Tablet legacy storage migration failed: {}",
                    migration.markerWrite() == null ? migration.marker() : migration.markerWrite().diagnostic());
        }
        punishmentsFile = storagePaths.punishmentsFile();
    }

    private static void backupBrokenFile() {
        if (punishmentsFile == null || !Files.exists(punishmentsFile)) return;

        String timestamp = BROKEN_TIMESTAMP.format(LocalDateTime.now());
        Path broken = storagePaths == null
                ? punishmentsFile.resolveSibling("punishments.broken." + timestamp + ".json")
                : storagePaths.backupsDirectory().resolve("punishments.broken." + timestamp + ".json");
        try {
            Files.createDirectories(broken.getParent());
            Files.copy(punishmentsFile, broken, StandardCopyOption.REPLACE_EXISTING);
            TacticalTabletMod.LOGGER.warn("Backed up broken Tactical Tablet punishments file to {}", broken);
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to back up broken Tactical Tablet punishments file {}", punishmentsFile, exception);
        }
    }

    private static RemovalResult removeAndSave(UUID target, Map<UUID, PunishmentRecord> records) {
        if (target == null) {
            return new RemovalResult(RemovalStatus.NOT_FOUND, null);
        }
        PunishmentRecord removed = records.remove(target);
        if (removed == null) {
            return new RemovalResult(RemovalStatus.NOT_FOUND, null);
        }
        FileSaveResult result = saveAtomic();
        if (result.status() == FileSaveResult.Status.SUCCESS) {
            return new RemovalResult(RemovalStatus.REMOVED, result);
        }
        records.put(target, removed);
        return new RemovalResult(RemovalStatus.STORAGE_ERROR, result);
    }

    private static void restoreRecord(Map<UUID, PunishmentRecord> records, UUID target, PunishmentRecord previous) {
        if (previous == null) {
            records.remove(target);
        } else {
            records.put(target, previous);
        }
    }

    private static Path uninitializedPath() {
        return punishmentsFile == null ? Path.of(PUNISHMENTS_FILE) : punishmentsFile;
    }

    public enum RemovalStatus {
        REMOVED,
        NOT_FOUND,
        STORAGE_ERROR
    }

    public record RemovalResult(RemovalStatus status, FileSaveResult storageResult) {
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
