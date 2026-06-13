package com.makar.tacticaltablet.progression;

import com.makar.tacticaltablet.core.TacticalTabletMod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class PlayerProgressManager {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final String DATA_DIRECTORY = "tacticaltablet_data";
    private static final String PLAYERS_DIRECTORY = "players";
    private static final String BACKUPS_DIRECTORY = "backups";
    private static final String SEASON_FILE = "season.json";

    private static final int DATA_VERSION = 5;
    public static final int STANDARD_TIER = 0;
    public static final int EPIC_TIER = 1;
    public static final int LEGEND_TIER = 2;
    public static final int EPIC_XP = 300;
    public static final int LEGEND_XP = 800;
    public static final int BASE_UNLOCK_COST = 25;
    public static final int EPIC_UPGRADE_COST = 20;
    public static final int LEGEND_UPGRADE_COST = 50;

    public static final int KILL_COIN_REWARD = 2;
    public static final int WIN_COIN_REWARD = 5;

    private static final String[] INITIAL_BASE_CLASSES = new String[]{
            "stormtrooper",
            "sniper",
            "scout"
    };

    private static final String[] BASE_CLASSES = new String[]{
            "stormtrooper",
            "sniper",
            "scout",
            "droneoperator",
            "machinegunner",
            "mortarman",
            "rpgtrooper"
    };

    private static final String[] SHOP_CLASSES = new String[]{
            "boomguy",
            "dream",
            "tagilla",
            "blackops",
            "cowboy",
            "solider",
            "rebel"
    };

    private static final String[] ALL_CLASSES = new String[]{
            "stormtrooper",
            "sniper",
            "scout",
            "droneoperator",
            "machinegunner",
            "mortarman",
            "rpgtrooper",
            "boomguy",
            "dream",
            "tagilla",
            "blackops",
            "cowboy",
            "solider",
            "rebel"
    };

    private static final Map<String, Integer> SHOP_CLASS_PRICES = Map.of(
            "boomguy", 500,
            "dream", 500,
            "tagilla", 750,
            "blackops", 1000,
            "cowboy", 100,
            "solider", 50,
            "rebel", 1000
    );

    private static final Map<String, Integer> SHOP_CLASS_LEVELS = Map.of(
            "boomguy", 2,
            "dream", 2,
            "tagilla", 2,
            "blackops", 2,
            "cowboy", 1,
            "solider", 0,
            "rebel", 2
    );

    private static final int AUTOSAVE_INTERVAL_TICKS = 20 * 60;
    private static final int BACKUP_INTERVAL_TICKS = 20 * 60 * 30;
    private static final int MAX_BACKUP_FOLDERS = 48;

    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd_HH-mm-ss")
            .withZone(ZoneId.systemDefault());

    private static final Map<String, PlayerProgress> cache = new HashMap<>();
    private static final Map<String, Boolean> dirty = new HashMap<>();

    private static Path dataRoot;
    private static Path playersRoot;
    private static Path backupsRoot;
    private static int autosaveTicks;
    private static int backupTicks;

    public enum PurchaseResult {
        PURCHASED,
        ALREADY_OWNED,
        NOT_ENOUGH_COINS,
        NOT_PURCHASABLE
    }

    public enum ProgressionResult {
        SUCCESS,
        ALREADY_UNLOCKED,
        LOCKED,
        NOT_ENOUGH_COINS,
        NOT_ENOUGH_XP,
        INVALID_CLASS,
        MAX_TIER,
        WRONG_TIER
    }

    public static String[] getStandardClasses() {
        return BASE_CLASSES.clone();
    }

    public static String[] getInitialBaseClasses() {
        return INITIAL_BASE_CLASSES.clone();
    }

    public static String[] getUnlockableBaseClasses() {
        return java.util.Arrays.stream(BASE_CLASSES)
                .filter(PlayerProgressManager::isUnlockableBaseClass)
                .toArray(String[]::new);
    }

    public static String[] getShopClasses() {
        return SHOP_CLASSES.clone();
    }

    public static String[] getAllClasses() {
        return ALL_CLASSES.clone();
    }

    public static int getShopPrice(String clazz) {
        return SHOP_CLASS_PRICES.getOrDefault(normalizeClass(clazz), 0);
    }

    public static int getShopFixedLevel(String clazz) {
        return SHOP_CLASS_LEVELS.getOrDefault(normalizeClass(clazz), 0);
    }

    public static boolean isShopClass(String clazz) {
        return SHOP_CLASS_PRICES.containsKey(normalizeClass(clazz));
    }

    public static boolean isInitialBaseClass(String clazz) {
        return containsClass(INITIAL_BASE_CLASSES, clazz);
    }

    public static boolean isUnlockableBaseClass(String clazz) {
        String normalizedClass = normalizeClass(clazz);
        return isBaseProgressionClass(normalizedClass) && !isInitialBaseClass(normalizedClass);
    }

    public static boolean isBaseProgressionClass(String clazz) {
        return containsClass(BASE_CLASSES, clazz);
    }

    public static int getBaseUnlockCost(String clazz) {
        return isUnlockableBaseClass(clazz) ? BASE_UNLOCK_COST : 0;
    }

    public static int getUpgradeCost(int targetTier) {
        if (targetTier == EPIC_TIER) return EPIC_UPGRADE_COST;
        if (targetTier == LEGEND_TIER) return LEGEND_UPGRADE_COST;
        return 0;
    }

    public static int getXpCapForTier(int tier) {
        if (tier >= LEGEND_TIER) return LEGEND_XP;
        if (tier == EPIC_TIER) return LEGEND_XP;
        return EPIC_XP;
    }

    public static synchronized void loadPlayer(ServerPlayer player) {
        if (player == null) return;

        init(player.server);

        String key = getPlayerKey(player);
        PlayerProgress progress = cache.get(key);
        boolean fileExists = Files.exists(getPlayerFile(key));

        if (progress == null) {
            progress = readOrCreateProgress(player, key);
            cache.put(key, progress);
        }

        int oldVersion = progress.dataVersion;
        boolean changed = updateIdentity(progress, player);
        normalize(progress);

        if (oldVersion < DATA_VERSION) {
            changed = true;
        }

        if (changed || !fileExists) {
            markDirty(key);
            savePlayer(player);
        }
    }

    public static synchronized void savePlayer(ServerPlayer player) {
        if (player == null) return;

        init(player.server);

        String key = getPlayerKey(player);
        PlayerProgress progress = getOrLoad(player, key);

        updateIdentity(progress, player);
        progress.lastSeen = Instant.now().toEpochMilli();
        normalize(progress);

        try {
            writeJsonAtomically(getPlayerFile(key), progress);
            dirty.remove(key);
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to save Tactical Tablet progress for {}", key, exception);
        }
    }

    public static synchronized void saveAll() {
        if (playersRoot == null) return;

        for (Map.Entry<String, PlayerProgress> entry : cache.entrySet()) {
            String key = entry.getKey();
            PlayerProgress progress = entry.getValue();

            progress.lastSeen = Instant.now().toEpochMilli();
            normalize(progress);

            try {
                writeJsonAtomically(getPlayerFile(key), progress);
                dirty.remove(key);
            } catch (IOException exception) {
                TacticalTabletMod.LOGGER.error("Failed to save Tactical Tablet progress for {}", key, exception);
            }
        }
    }

    public static synchronized void resetStorage() {
        saveAll();
        cache.clear();
        dirty.clear();
        dataRoot = null;
        playersRoot = null;
        backupsRoot = null;
        autosaveTicks = 0;
        backupTicks = 0;
    }

    public static synchronized int getXP(ServerPlayer player, String clazz) {
        if (player == null || clazz == null) return 0;

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));
        return progress.classes.getOrDefault(normalizeClass(clazz), 0);
    }

    public static synchronized void addXP(ServerPlayer player, String clazz, int amount) {
        if (player == null || clazz == null || clazz.isBlank() || amount <= 0) return;

        String normalizedClass = normalizeClass(clazz);
        if (!canGainXp(player, normalizedClass)) return;

        String key = getPlayerKey(player);
        PlayerProgress progress = getOrLoad(player, key);
        int current = progress.classes.getOrDefault(normalizedClass, 0);
        int tier = getStoredTier(progress, normalizedClass);
        int capped = Math.min(getXpCapForTier(tier), safeAdd(current, amount));

        progress.classes.put(normalizedClass, capped);
        markDirty(key);
    }

    public static synchronized void setXP(ServerPlayer player, String clazz, int amount) {
        if (player == null || clazz == null || clazz.isBlank()) return;

        String normalizedClass = normalizeClass(clazz);
        String key = getPlayerKey(player);
        PlayerProgress progress = getOrLoad(player, key);
        int value = isShopClass(normalizedClass) ? 0 : Math.max(0, amount);
        if (isBaseProgressionClass(normalizedClass)) {
            value = Math.min(value, LEGEND_XP);
        }
        progress.classes.put(normalizedClass, value);
        markDirty(key);
    }

    public static synchronized int getLevel(ServerPlayer player, String clazz) {
        String normalizedClass = normalizeClass(clazz);

        if (isShopClass(normalizedClass)) {
            return isClassPurchased(player, normalizedClass) ? getShopFixedLevel(normalizedClass) : 0;
        }

        if (!isBaseClassUnlocked(player, normalizedClass)) {
            return STANDARD_TIER;
        }

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));
        return getStoredTier(progress, normalizedClass);
    }

    public static int getLevelForXP(int xp) {
        if (xp >= LEGEND_XP) return 2;
        if (xp >= EPIC_XP) return 1;
        return 0;
    }

    public static synchronized boolean isBaseClassUnlocked(ServerPlayer player, String clazz) {
        if (player == null || clazz == null) return false;

        String normalizedClass = normalizeClass(clazz);
        if (!isBaseProgressionClass(normalizedClass)) return false;

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));
        return isBaseClassUnlocked(progress, normalizedClass);
    }

    public static synchronized boolean canGainXp(ServerPlayer player, String clazz) {
        if (player == null || clazz == null) return false;
        String normalizedClass = normalizeClass(clazz);
        return isBaseProgressionClass(normalizedClass) && isBaseClassUnlocked(player, normalizedClass);
    }

    public static synchronized ProgressionResult unlockBaseClass(ServerPlayer player, String clazz) {
        if (player == null || clazz == null) return ProgressionResult.INVALID_CLASS;

        String normalizedClass = normalizeClass(clazz);
        if (!isUnlockableBaseClass(normalizedClass)) {
            return ProgressionResult.INVALID_CLASS;
        }

        String key = getPlayerKey(player);
        PlayerProgress progress = getOrLoad(player, key);
        if (isBaseClassUnlocked(progress, normalizedClass)) {
            return ProgressionResult.ALREADY_UNLOCKED;
        }

        if (progress.coins < BASE_UNLOCK_COST) {
            return ProgressionResult.NOT_ENOUGH_COINS;
        }

        progress.coins -= BASE_UNLOCK_COST;
        progress.unlockedBaseClasses.put(normalizedClass, 1);
        progress.classes.putIfAbsent(normalizedClass, 0);
        progress.classTiers.putIfAbsent(normalizedClass, STANDARD_TIER);
        markDirty(key);
        return ProgressionResult.SUCCESS;
    }

    public static synchronized ProgressionResult upgradeClassTier(ServerPlayer player, String clazz, int targetTier) {
        if (player == null || clazz == null) return ProgressionResult.INVALID_CLASS;

        String normalizedClass = normalizeClass(clazz);
        if (!isBaseProgressionClass(normalizedClass)) {
            return ProgressionResult.INVALID_CLASS;
        }

        if (targetTier != EPIC_TIER && targetTier != LEGEND_TIER) {
            return ProgressionResult.INVALID_CLASS;
        }

        String key = getPlayerKey(player);
        PlayerProgress progress = getOrLoad(player, key);
        if (!isBaseClassUnlocked(progress, normalizedClass)) {
            return ProgressionResult.LOCKED;
        }

        int currentTier = getStoredTier(progress, normalizedClass);
        if (currentTier >= LEGEND_TIER) {
            return ProgressionResult.MAX_TIER;
        }

        if (targetTier != currentTier + 1) {
            return ProgressionResult.WRONG_TIER;
        }

        int requiredXp = targetTier == EPIC_TIER ? EPIC_XP : LEGEND_XP;
        if (progress.classes.getOrDefault(normalizedClass, 0) < requiredXp) {
            return ProgressionResult.NOT_ENOUGH_XP;
        }

        int cost = getUpgradeCost(targetTier);
        if (progress.coins < cost) {
            return ProgressionResult.NOT_ENOUGH_COINS;
        }

        progress.coins -= cost;
        progress.classTiers.put(normalizedClass, targetTier);
        progress.classes.put(normalizedClass, Math.min(progress.classes.getOrDefault(normalizedClass, 0), getXpCapForTier(targetTier)));
        markDirty(key);
        return ProgressionResult.SUCCESS;
    }

    public static synchronized void addCoins(ServerPlayer player, int amount) {
        if (player == null || amount == 0) return;

        String key = getPlayerKey(player);
        PlayerProgress progress = getOrLoad(player, key);
        progress.coins = Math.max(0, safeAdd(progress.coins, amount));
        markDirty(key);
    }

    public static synchronized int getCoins(ServerPlayer player) {
        if (player == null) return 0;

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));
        return progress.coins;
    }

    public static synchronized void setCoins(ServerPlayer player, int amount) {
        if (player == null) return;

        String key = getPlayerKey(player);
        PlayerProgress progress = getOrLoad(player, key);
        progress.coins = Math.max(0, amount);
        markDirty(key);
    }

    public static synchronized void addMatchPlayed(ServerPlayer player) {
        if (player == null) return;

        String key = getPlayerKey(player);
        PlayerProgress progress = getOrLoad(player, key);
        progress.matchesPlayed = safeAdd(progress.matchesPlayed, 1);
        markDirty(key);
    }

    public static synchronized int getMatchesPlayed(ServerPlayer player) {
        if (player == null) return 0;

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));
        return progress.matchesPlayed;
    }

    public static synchronized void addWin(ServerPlayer player) {
        if (player == null) return;

        String key = getPlayerKey(player);
        PlayerProgress progress = getOrLoad(player, key);
        progress.wins = safeAdd(progress.wins, 1);
        markDirty(key);
    }

    public static synchronized int getWins(ServerPlayer player) {
        if (player == null) return 0;

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));
        return progress.wins;
    }

    public static synchronized void addKill(ServerPlayer player) {
        if (player == null) return;

        String key = getPlayerKey(player);
        PlayerProgress progress = getOrLoad(player, key);
        progress.kills = safeAdd(progress.kills, 1);
        markDirty(key);
    }

    public static synchronized int getKills(ServerPlayer player) {
        if (player == null) return 0;

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));
        return progress.kills;
    }

    public static synchronized void addDeath(ServerPlayer player) {
        if (player == null) return;

        String key = getPlayerKey(player);
        PlayerProgress progress = getOrLoad(player, key);
        progress.deaths = safeAdd(progress.deaths, 1);
        markDirty(key);
    }

    public static synchronized int getDeaths(ServerPlayer player) {
        if (player == null) return 0;

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));
        return progress.deaths;
    }

    public static synchronized boolean isClassPurchased(ServerPlayer player, String clazz) {
        if (player == null || clazz == null) return false;

        String normalizedClass = normalizeClass(clazz);
        if (!isShopClass(normalizedClass)) return false;

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));
        return progress.purchasedClasses.getOrDefault(normalizedClass, 0) > 0;
    }

    public static synchronized PurchaseResult purchaseClass(ServerPlayer player, String clazz) {
        if (player == null || clazz == null) return PurchaseResult.NOT_PURCHASABLE;

        String normalizedClass = normalizeClass(clazz);
        int price = getShopPrice(normalizedClass);

        if (price <= 0) {
            return PurchaseResult.NOT_PURCHASABLE;
        }

        String key = getPlayerKey(player);
        PlayerProgress progress = getOrLoad(player, key);

        if (progress.purchasedClasses.getOrDefault(normalizedClass, 0) > 0) {
            return PurchaseResult.ALREADY_OWNED;
        }

        if (progress.coins < price) {
            return PurchaseResult.NOT_ENOUGH_COINS;
        }

        progress.coins -= price;
        progress.purchasedClasses.put(normalizedClass, 1);
        progress.classes.put(normalizedClass, 0);
        markDirty(key);
        return PurchaseResult.PURCHASED;
    }

    public static synchronized Map<String, Integer> getPurchasedClasses(ServerPlayer player) {
        Map<String, Integer> result = new HashMap<>();
        if (player == null) return result;

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));

        for (String clazz : SHOP_CLASSES) {
            String normalizedClass = normalizeClass(clazz);
            result.put(normalizedClass, progress.purchasedClasses.getOrDefault(normalizedClass, 0) > 0 ? 1 : 0);
        }

        return result;
    }

    public static synchronized Map<String, Integer> getUnlockedBaseClasses(ServerPlayer player) {
        Map<String, Integer> result = new HashMap<>();
        if (player == null) return result;

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));
        for (String clazz : BASE_CLASSES) {
            String normalizedClass = normalizeClass(clazz);
            result.put(normalizedClass, isBaseClassUnlocked(progress, normalizedClass) ? 1 : 0);
        }
        return result;
    }

    public static synchronized Map<String, Integer> getClassTiers(ServerPlayer player) {
        Map<String, Integer> result = new HashMap<>();
        if (player == null) return result;

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));
        for (String clazz : BASE_CLASSES) {
            String normalizedClass = normalizeClass(clazz);
            result.put(normalizedClass, getStoredTier(progress, normalizedClass));
        }
        for (String clazz : SHOP_CLASSES) {
            String normalizedClass = normalizeClass(clazz);
            result.put(normalizedClass, isClassPurchased(player, normalizedClass) ? getShopFixedLevel(normalizedClass) : STANDARD_TIER);
        }
        return result;
    }

    public static synchronized int getCareerProgressPercent(ServerPlayer player) {
        if (player == null) return 0;

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));
        double completed = 0.0D;

        for (String clazz : BASE_CLASSES) {
            String normalizedClass = normalizeClass(clazz);
            if (!isBaseClassUnlocked(progress, normalizedClass)) continue;
            int xp = progress.classes.getOrDefault(normalizeClass(clazz), 0);
            completed += Math.min(Math.max(xp, 0), LEGEND_XP) / (double) LEGEND_XP;
        }

        for (String clazz : SHOP_CLASSES) {
            if (progress.purchasedClasses.getOrDefault(normalizeClass(clazz), 0) > 0) {
                completed += 1.0D;
            }
        }

        int totalGoals = BASE_CLASSES.length + SHOP_CLASSES.length;
        if (totalGoals <= 0) return 100;

        return Math.max(0, Math.min(100, (int) Math.round(completed * 100.0D / totalGoals)));
    }

    public static synchronized int getBattlePassXp(ServerPlayer player) {
        if (player == null) return 0;

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));
        return progress.battlePassXp;
    }

    public static synchronized void addBattlePassXp(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) return;

        String key = getPlayerKey(player);
        PlayerProgress progress = getOrLoad(player, key);
        progress.battlePassXp = safeAdd(progress.battlePassXp, amount);
        markDirty(key);
    }

    public static synchronized void tick(MinecraftServer server) {
        if (server == null) return;

        init(server);

        autosaveTicks++;
        backupTicks++;

        if (autosaveTicks >= AUTOSAVE_INTERVAL_TICKS) {
            autosaveTicks = 0;
            saveDirty();
        }

        if (backupTicks >= BACKUP_INTERVAL_TICKS) {
            backupTicks = 0;
            backupNow();
        }
    }

    public static synchronized void unloadPlayer(ServerPlayer player) {
        if (player == null) return;

        String key = getPlayerKey(player);
        cache.remove(key);
        dirty.remove(key);
    }

    public static synchronized Map<String, Integer> getAllClassXP(ServerPlayer player) {
        Map<String, Integer> result = new HashMap<>();
        if (player == null) return result;

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));

        for (String clazz : ALL_CLASSES) {
            String normalizedClass = normalizeClass(clazz);
            int xp = isShopClass(normalizedClass) ? 0 : progress.classes.getOrDefault(normalizedClass, 0);
            result.put(normalizedClass, xp);
        }

        return result;
    }

    public static synchronized Map<String, Integer> getAllClassLevels(ServerPlayer player) {
        Map<String, Integer> result = new HashMap<>();
        if (player == null) return result;

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));

        for (String clazz : ALL_CLASSES) {
            String normalizedClass = normalizeClass(clazz);
            int level = isShopClass(normalizedClass)
                    ? progress.purchasedClasses.getOrDefault(normalizedClass, 0) > 0 ? getShopFixedLevel(normalizedClass) : STANDARD_TIER
                    : getStoredTier(progress, normalizedClass);
            result.put(normalizedClass, level);
        }

        return result;
    }

    public static synchronized void backupNow() {
        if (playersRoot == null || backupsRoot == null) return;

        saveAll();

        String timestamp = BACKUP_FORMAT.format(Instant.now());
        Path backupRoot = backupsRoot.resolve(timestamp);
        Path backupPlayersRoot = backupRoot.resolve(PLAYERS_DIRECTORY);

        try {
            Files.createDirectories(backupPlayersRoot);
            copyJsonFiles(playersRoot, backupPlayersRoot);

            Path seasonFile = dataRoot.resolve(SEASON_FILE);
            if (Files.exists(seasonFile)) {
                Files.copy(
                        seasonFile,
                        backupRoot.resolve(SEASON_FILE),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            cleanOldBackups();
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to create Tactical Tablet progress backup", exception);
        }
    }

    public static synchronized Path getDataRoot() {
        return dataRoot;
    }

    private static PlayerProgress getOrLoad(ServerPlayer player, String key) {
        init(player.server);

        PlayerProgress cached = cache.get(key);
        if (cached != null) {
            updateIdentity(cached, player);
            return cached;
        }

        PlayerProgress progress = readOrCreateProgress(player, key);
        updateIdentity(progress, player);
        normalize(progress);
        cache.put(key, progress);
        return progress;
    }

    private static PlayerProgress readOrCreateProgress(ServerPlayer player, String key) {
        Path file = getPlayerFile(key);

        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                PlayerProgress progress = GSON.fromJson(reader, PlayerProgress.class);
                return progress == null ? createProgress(player) : progress;
            } catch (JsonSyntaxException | IOException exception) {
                TacticalTabletMod.LOGGER.error("Failed to read Tactical Tablet progress file {}", file, exception);
                backupCorruptFile(file);
            }
        }

        Path legacyFile = getLegacyPlayerFile(player);
        if (legacyFile != null && !legacyFile.equals(file) && Files.exists(legacyFile)) {
            try (Reader reader = Files.newBufferedReader(legacyFile, StandardCharsets.UTF_8)) {
                PlayerProgress progress = GSON.fromJson(reader, PlayerProgress.class);
                TacticalTabletMod.LOGGER.info(
                        "Migrating Tactical Tablet progress for {} from name key {} to UUID key {}",
                        player.getGameProfile().getName(),
                        legacyFile.getFileName(),
                        file.getFileName()
                );
                return progress == null ? createProgress(player) : progress;
            } catch (JsonSyntaxException | IOException exception) {
                TacticalTabletMod.LOGGER.error("Failed to read legacy Tactical Tablet progress file {}", legacyFile, exception);
                backupCorruptFile(legacyFile);
            }
        }

        return createProgress(player);
    }

    private static PlayerProgress createProgress(ServerPlayer player) {
        PlayerProgress progress = new PlayerProgress();
        progress.name = player.getGameProfile().getName();
        progress.uuid = compactUuid(player);
        progress.firstSeen = Instant.now().toEpochMilli();
        progress.lastSeen = progress.firstSeen;
        normalize(progress);
        return progress;
    }

    private static boolean updateIdentity(PlayerProgress progress, ServerPlayer player) {
        boolean changed = false;
        String name = player.getGameProfile().getName();
        String uuid = compactUuid(player);

        if (!Objects.equals(progress.name, name)) {
            progress.name = name;
            changed = true;
        }

        if (!Objects.equals(progress.uuid, uuid)) {
            progress.uuid = uuid;
            changed = true;
        }

        if (progress.firstSeen <= 0L) {
            progress.firstSeen = Instant.now().toEpochMilli();
            changed = true;
        }

        progress.lastSeen = Instant.now().toEpochMilli();
        return changed;
    }

    private static void init(MinecraftServer server) {
        if (server == null || dataRoot != null) return;

        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path serverRoot = worldRoot.getParent();

        if (serverRoot == null) {
            serverRoot = worldRoot;
        }

        dataRoot = serverRoot.resolve(DATA_DIRECTORY);
        playersRoot = dataRoot.resolve(PLAYERS_DIRECTORY);
        backupsRoot = dataRoot.resolve(BACKUPS_DIRECTORY);

        try {
            Files.createDirectories(playersRoot);
            Files.createDirectories(backupsRoot);
            ensureSeasonFile();
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to initialize Tactical Tablet progress storage", exception);
        }
    }

    private static void ensureSeasonFile() throws IOException {
        Path seasonFile = dataRoot.resolve(SEASON_FILE);

        if (Files.exists(seasonFile)) return;

        Map<String, Object> season = new HashMap<>();
        season.put("dataVersion", DATA_VERSION);
        season.put("season", 1);
        season.put("createdAt", Instant.now().toString());

        writeJsonAtomically(seasonFile, season);
    }

    private static void saveDirty() {
        if (dirty.isEmpty()) return;

        for (String key : dirty.keySet().toArray(new String[0])) {
            PlayerProgress progress = cache.get(key);
            if (progress == null) {
                dirty.remove(key);
                continue;
            }

            progress.lastSeen = Instant.now().toEpochMilli();
            normalize(progress);

            try {
                writeJsonAtomically(getPlayerFile(key), progress);
                dirty.remove(key);
            } catch (IOException exception) {
                TacticalTabletMod.LOGGER.error("Failed to autosave Tactical Tablet progress for {}", key, exception);
            }
        }
    }

    private static void markDirty(String key) {
        dirty.put(key, Boolean.TRUE);
    }

    private static void normalize(PlayerProgress progress) {
        int oldVersion = progress.dataVersion;
        progress.dataVersion = DATA_VERSION;

        if (progress.name == null) {
            progress.name = "";
        }

        if (progress.uuid == null) {
            progress.uuid = "";
        }

        progress.wins = Math.max(0, progress.wins);
        progress.kills = Math.max(0, progress.kills);
        progress.deaths = Math.max(0, progress.deaths);
        progress.matchesPlayed = Math.max(0, progress.matchesPlayed);
        progress.coins = Math.max(0, progress.coins);
        progress.battlePassXp = Math.max(0, progress.battlePassXp);

        progress.classes = normalizeIntegerMap(progress.classes);
        progress.classTiers = normalizeIntegerMap(progress.classTiers);
        progress.unlockedBaseClasses = normalizeIntegerMap(progress.unlockedBaseClasses);
        progress.purchasedClasses = normalizeIntegerMap(progress.purchasedClasses);
        progress.donations = normalizeIntegerMap(progress.donations);
        progress.stats = normalizeIntegerMap(progress.stats);

        for (String clazz : INITIAL_BASE_CLASSES) {
            progress.unlockedBaseClasses.put(normalizeClass(clazz), 1);
        }

        if (oldVersion < 5) {
            migrateLegacyBaseProgress(progress);
        }

        for (String clazz : ALL_CLASSES) {
            String normalizedClass = normalizeClass(clazz);
            progress.classes.putIfAbsent(normalizedClass, 0);

            if (isShopClass(normalizedClass)) {
                progress.classes.put(normalizedClass, 0);
            }
        }

        for (String clazz : BASE_CLASSES) {
            String normalizedClass = normalizeClass(clazz);
            progress.classes.put(normalizedClass, Math.min(Math.max(0, progress.classes.getOrDefault(normalizedClass, 0)), LEGEND_XP));
            progress.classTiers.put(normalizedClass, clampTier(progress.classTiers.getOrDefault(normalizedClass, STANDARD_TIER)));
            progress.unlockedBaseClasses.put(normalizedClass, isBaseClassUnlocked(progress, normalizedClass) ? 1 : 0);
        }

        for (String clazz : SHOP_CLASSES) {
            progress.purchasedClasses.putIfAbsent(normalizeClass(clazz), 0);
        }
    }

    private static void migrateLegacyBaseProgress(PlayerProgress progress) {
        for (String clazz : BASE_CLASSES) {
            String normalizedClass = normalizeClass(clazz);
            int xp = Math.min(Math.max(0, progress.classes.getOrDefault(normalizedClass, 0)), LEGEND_XP);

            if (xp > 0 || isInitialBaseClass(normalizedClass)) {
                progress.unlockedBaseClasses.put(normalizedClass, 1);
            }

            if (!progress.classTiers.containsKey(normalizedClass)) {
                progress.classTiers.put(normalizedClass, getLevelForXP(xp));
            }
        }
    }

    private static boolean isBaseClassUnlocked(PlayerProgress progress, String clazz) {
        if (progress == null) return false;
        String normalizedClass = normalizeClass(clazz);
        return isInitialBaseClass(normalizedClass)
                || progress.unlockedBaseClasses.getOrDefault(normalizedClass, 0) > 0;
    }

    private static int getStoredTier(PlayerProgress progress, String clazz) {
        if (progress == null) return STANDARD_TIER;
        return clampTier(progress.classTiers.getOrDefault(normalizeClass(clazz), STANDARD_TIER));
    }

    private static int clampTier(int tier) {
        return Math.max(STANDARD_TIER, Math.min(LEGEND_TIER, tier));
    }

    private static boolean containsClass(String[] classes, String clazz) {
        String normalizedClass = normalizeClass(clazz);
        for (String candidate : classes) {
            if (normalizeClass(candidate).equals(normalizedClass)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Integer> normalizeIntegerMap(Map<String, Integer> input) {
        Map<String, Integer> result = new HashMap<>();

        if (input == null) {
            return result;
        }

        for (Map.Entry<String, Integer> entry : input.entrySet()) {
            String key = normalizeClass(entry.getKey());
            if (key.isBlank()) continue;

            result.put(key, Math.max(0, entry.getValue() == null ? 0 : entry.getValue()));
        }

        return result;
    }

    private static String normalizeClass(String clazz) {
        return clazz == null ? "" : clazz.trim().toLowerCase(Locale.ROOT);
    }

    private static String getPlayerKey(ServerPlayer player) {
        return compactUuid(player);
    }

    private static Path getLegacyPlayerFile(ServerPlayer player) {
        String legacyKey = getLegacyPlayerKey(player);
        if (legacyKey.isBlank()) return null;

        return getPlayerFile(legacyKey);
    }

    private static String getLegacyPlayerKey(ServerPlayer player) {
        String name = player.getGameProfile().getName();
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9_.-]", "_");

        if (normalized.isBlank()) {
            normalized = compactUuid(player);
        }

        return normalized;
    }

    private static String compactUuid(ServerPlayer player) {
        return player.getUUID().toString().replace("-", "");
    }

    private static Path getPlayerFile(String key) {
        return playersRoot.resolve(key + ".json");
    }

    private static void writeJsonAtomically(Path file, Object value) throws IOException {
        Files.createDirectories(file.getParent());

        Path tempFile = file.resolveSibling(file.getFileName().toString() + ".tmp");

        try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
            GSON.toJson(value, writer);
        }

        try {
            Files.move(
                    tempFile,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void copyJsonFiles(Path sourceRoot, Path targetRoot) throws IOException {
        if (!Files.isDirectory(sourceRoot)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourceRoot, "*.json")) {
            for (Path source : stream) {
                Files.copy(
                        source,
                        targetRoot.resolve(source.getFileName().toString()),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        }
    }

    private static void backupCorruptFile(Path file) {
        if (file == null || !Files.exists(file) || backupsRoot == null) return;

        String timestamp = BACKUP_FORMAT.format(Instant.now());
        Path target = backupsRoot.resolve("corrupt_" + timestamp + "_" + file.getFileName());

        try {
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to back up corrupt Tactical Tablet progress file {}", file, exception);
        }
    }

    private static void cleanOldBackups() throws IOException {
        if (!Files.isDirectory(backupsRoot)) return;

        try (Stream<Path> stream = Files.list(backupsRoot)) {
            Path[] backups = stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toArray(Path[]::new);

            for (int i = MAX_BACKUP_FOLDERS; i < backups.length; i++) {
                deleteRecursively(backups[i]);
            }
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;

        try (Stream<Path> stream = Files.walk(root)) {
            Path[] paths = stream
                    .sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new);

            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static int safeAdd(int current, int amount) {
        if (amount > 0 && current > Integer.MAX_VALUE - amount) {
            return Integer.MAX_VALUE;
        }

        if (amount < 0 && current < Integer.MIN_VALUE - amount) {
            return Integer.MIN_VALUE;
        }

        return current + amount;
    }

    private static final class PlayerProgress {
        private int dataVersion = DATA_VERSION;
        private String name = "";
        private String uuid = "";
        private Map<String, Integer> classes = new HashMap<>();
        private Map<String, Integer> classTiers = new HashMap<>();
        private Map<String, Integer> unlockedBaseClasses = new HashMap<>();
        private int wins;
        private int kills;
        private int deaths;
        private int matchesPlayed;
        private int coins;
        private int battlePassXp;
        private Map<String, Integer> purchasedClasses = new HashMap<>();
        private Map<String, Integer> donations = new HashMap<>();
        private Map<String, Integer> stats = new HashMap<>();
        private long firstSeen;
        private long lastSeen;
    }
}

