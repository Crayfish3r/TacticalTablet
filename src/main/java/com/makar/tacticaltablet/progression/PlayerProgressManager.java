package com.makar.tacticaltablet.progression;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.clan.transaction.CreateClanTransaction;
import com.makar.tacticaltablet.clan.transaction.RepositoryResult;
import com.makar.tacticaltablet.game.MapSetManager;
import com.makar.tacticaltablet.storage.AtomicFileStore;
import com.makar.tacticaltablet.storage.BackupCoordinator;
import com.makar.tacticaltablet.storage.FileSaveResult;
import com.makar.tacticaltablet.storage.ModPersistenceExecutor;
import com.makar.tacticaltablet.storage.SaveTicket;
import com.makar.tacticaltablet.storage.DurableSaveResult;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.Duration;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
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

    private static final int DATA_VERSION = 11;
    public static final int BASIC_TIER = ClassTier.BASIC.id();
    public static final int RARE_TIER = ClassTier.RARE.id();
    public static final int EPIC_TIER = ClassTier.EPIC.id();
    public static final int LEGEND_TIER = ClassTier.LEGEND.id();
    public static final int MONSTER_TIER = ClassTier.MONSTER.id();
    public static final int MAX_CLASS_XP = ClassTier.MAX_XP;
    public static final int BASE_UNLOCK_COST = 25;

    public static final int KILL_COIN_REWARD = 5;

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
            "rebel",
            "saboteur"
    };

    private static final String[] EXCLUSIVE_CLASSES = new String[]{
            "killer",
            "miniboss",
            "shahed",
            "krot",
            "medic",
            "microwave",
            "railgunner"
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
            "rebel",
            "saboteur"
    };

    private static final Map<String, Integer> SHOP_CLASS_PRICES = Map.of(
            "boomguy", 500,
            "dream", 500,
            "tagilla", 750,
            "blackops", 1000,
            "cowboy", 100,
            "solider", 50,
            "rebel", 1000,
            "saboteur", 1000
    );

    private static final Map<String, Integer> SHOP_CLASS_LEVELS = Map.of(
            "boomguy", LEGEND_TIER,
            "dream", LEGEND_TIER,
            "tagilla", LEGEND_TIER,
            "blackops", LEGEND_TIER,
            "cowboy", EPIC_TIER,
            "solider", BASIC_TIER,
            "rebel", LEGEND_TIER,
            "saboteur", LEGEND_TIER
    );

    private static final int AUTOSAVE_INTERVAL_TICKS = 20 * 60;
    private static final int BACKUP_INTERVAL_TICKS = 20 * 60 * 30;
    private static final int MAX_BACKUP_FOLDERS = 48;
    private static final int PERSISTENCE_QUEUE_LIMIT = 512;
    private static final Duration SHUTDOWN_FLUSH_TIMEOUT = Duration.ofSeconds(10);

    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd_HH-mm-ss")
            .withZone(ZoneId.systemDefault());
    private static final ProgressionRules PROGRESSION_RULES = new ProgressionRules(
            MONSTER_TIER,
            MAX_CLASS_XP,
            java.util.Arrays.stream(ClassTier.values()).map(ClassTier::requiredXp).toList()
    );
    private static final ProgressService PROGRESS_SERVICE = new ProgressService(new ProgressCatalog(
            Set.of(INITIAL_BASE_CLASSES),
            Set.of(BASE_CLASSES),
            SHOP_CLASS_PRICES,
            SHOP_CLASS_LEVELS,
            Set.of(EXCLUSIVE_CLASSES),
            BASE_UNLOCK_COST
    ));

    private static final Map<String, PlayerProgress> cache = new HashMap<>();
    private static final Map<String, Boolean> dirty = new HashMap<>();
    private static final Map<String, Long> snapshotRevisions = new HashMap<>();
    private static final BackupCoordinator backupCoordinator = new BackupCoordinator();
    private static final AtomicFileStore FILE_STORE = new AtomicFileStore();

    private static Path dataRoot;
    private static Path playersRoot;
    private static Path backupsRoot;
    private static int autosaveTicks;
    private static int backupTicks;
    private static long nextSnapshotRevision;
    private static long nextBackupRevision;
    private static ModPersistenceExecutor persistenceExecutor;

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

    public enum ExclusiveClassGrantResult {
        GRANTED,
        ALREADY_OWNED,
        INVALID_CLASS,
        SAVE_FAILED
    }

    /** Pure validation used by the server upgrade operation and regression tests. */
    public static ProgressionResult evaluateTierUpgrade(int currentTier, int xp, int coins, int targetTier) {
        ClassTier current = ClassTier.clamp(currentTier);
        if (current.isMaximum()) return ProgressionResult.MAX_TIER;

        Optional<ClassTier> target = ClassTier.byId(targetTier);
        if (target.isEmpty() || target.get() == ClassTier.BASIC) return ProgressionResult.INVALID_CLASS;
        if (target.get().id() != current.id() + 1) return ProgressionResult.WRONG_TIER;
        if (xp < target.get().requiredXp()) return ProgressionResult.NOT_ENOUGH_XP;
        if (coins < target.get().upgradeCost()) return ProgressionResult.NOT_ENOUGH_COINS;
        return ProgressionResult.SUCCESS;
    }

    public static int normalizePersistedTier(int tier) {
        return ClassTier.clamp(tier).id();
    }

    public static int normalizePersistedXp(int xp) {
        return ProgressPolicy.normalizeExperience(xp, MAX_CLASS_XP);
    }

    /** Value-only migration contract for persisted class entries. It never remaps a valid tier ID from v10. */
    public static PersistedClassProgress migrateClassProgress(int dataVersion, int tier, int xp) {
        return new PersistedClassProgress(DATA_VERSION, normalizePersistedTier(tier), normalizePersistedXp(xp));
    }

    public record PersistedClassProgress(int dataVersion, int tier, int xp) { }

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

    public static String[] getExclusiveClasses() {
        return EXCLUSIVE_CLASSES.clone();
    }

    public static int getShopPrice(String clazz) {
        return SHOP_CLASS_PRICES.getOrDefault(normalizeClass(clazz), 0);
    }

    public static int getShopFixedLevel(String clazz) {
        return SHOP_CLASS_LEVELS.getOrDefault(normalizeClass(clazz), BASIC_TIER);
    }

    public static boolean isShopClass(String clazz) {
        return SHOP_CLASS_PRICES.containsKey(normalizeClass(clazz));
    }

    public static boolean isExclusiveClass(String clazz) {
        return containsClass(EXCLUSIVE_CLASSES, clazz);
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
        return ClassTier.byId(targetTier).map(ClassTier::upgradeCost).orElse(0);
    }

    public static int getXpCapForTier(int tier) {
        return ClassTier.clamp(tier).xpCap();
    }

    public static int getRequiredXpForTier(int tier) {
        return ClassTier.byId(tier).map(ClassTier::requiredXp).orElse(0);
    }

    public static String getTierDisplayName(int tier) {
        return ClassTier.clamp(tier).displayName();
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
        boolean progressionValuesInvalid = requiresProgressionNormalization(progress);
        normalize(progress);

        if (oldVersion < DATA_VERSION || progressionValuesInvalid) {
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

        enqueueSnapshot(key, progress);
    }

    public static synchronized void saveAll() {
        saveAll(false);
    }

    private static void saveAll(boolean finalSnapshots) {
        if (playersRoot == null) return;

        for (Map.Entry<String, PlayerProgress> entry : cache.entrySet()) {
            String key = entry.getKey();
            PlayerProgress progress = entry.getValue();

            progress.lastSeen = Instant.now().toEpochMilli();
            normalize(progress);
            enqueueSnapshot(key, progress, finalSnapshots);
        }
    }

    public static synchronized void resetStorage() {
        if (persistenceExecutor != null) {
            persistenceExecutor.stopAccepting();
            persistenceExecutor.close();
        }
        cache.clear();
        dirty.clear();
        snapshotRevisions.clear();
        dataRoot = null;
        playersRoot = null;
        backupsRoot = null;
        autosaveTicks = 0;
        backupTicks = 0;
        nextSnapshotRevision = 0L;
        nextBackupRevision = 0L;
        persistenceExecutor = null;
    }

    public static synchronized int getXP(ServerPlayer player, String clazz) {
        if (player == null || clazz == null) return 0;

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));
        return progress.classes.getOrDefault(normalizeClass(clazz), 0);
    }

    public static synchronized int addXP(ServerPlayer player, String clazz, int amount) {
        if (player == null || clazz == null || clazz.isBlank() || amount <= 0) return 0;

        String normalizedClass = normalizeClass(clazz);
        if (!canGainXp(player, normalizedClass)) return 0;

        String key = getPlayerKey(player);
        PlayerProgress progress = getOrLoad(player, key);
        ExperienceMutationResult result = PROGRESS_SERVICE.addExperience(
                progress, normalizedClass, amount, PROGRESSION_RULES);
        markDirty(key);
        return result.awardedExperience();
    }

    public static synchronized boolean isXpBoostEnabled(ServerPlayer player) {
        if (player == null) return false;
        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));
        return progress.xpBoost;
    }

    public static synchronized void setXpBoostEnabled(ServerPlayer player, boolean enabled) {
        if (player == null) return;

        String key = getPlayerKey(player);
        PlayerProgress progress = getOrLoad(player, key);
        if (!PROGRESS_SERVICE.setFlag(progress, MutableProgressState.Flag.XP_BOOST, enabled)) return;
        markDirty(key);
    }

    public static synchronized boolean isXpBoostEnabled(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) return false;
        PlayerProgress progress = getOrLoad(server, uuid, "");
        return progress.xpBoost;
    }

    public static synchronized void setXpBoostEnabled(MinecraftServer server, UUID uuid, String lastKnownName, boolean enabled) {
        if (server == null || uuid == null) return;

        PlayerProgress progress = getOrLoad(server, uuid, lastKnownName);
        if (!PROGRESS_SERVICE.setFlag(progress, MutableProgressState.Flag.XP_BOOST, enabled)) {
            saveOffline(uuid, progress);
            return;
        }
        saveOffline(uuid, progress);
    }

    public static synchronized boolean isSadTromboneKillsEnabled(ServerPlayer player) {
        if (player == null) return false;
        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));
        return progress.sadTromboneKills;
    }

    public static synchronized void setSadTromboneKillsEnabled(ServerPlayer player, boolean enabled) {
        if (player == null) return;

        String key = getPlayerKey(player);
        PlayerProgress progress = getOrLoad(player, key);
        if (!PROGRESS_SERVICE.setFlag(progress, MutableProgressState.Flag.SAD_TROMBONE_KILLS, enabled)) return;
        markDirty(key);
    }

    public static synchronized boolean isSadTromboneKillsEnabled(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) return false;
        PlayerProgress progress = getOrLoad(server, uuid, "");
        return progress.sadTromboneKills;
    }

    public static synchronized void setSadTromboneKillsEnabled(MinecraftServer server, UUID uuid, String lastKnownName, boolean enabled) {
        if (server == null || uuid == null) return;

        PlayerProgress progress = getOrLoad(server, uuid, lastKnownName);
        if (!PROGRESS_SERVICE.setFlag(progress, MutableProgressState.Flag.SAD_TROMBONE_KILLS, enabled)) {
            saveOffline(uuid, progress);
            return;
        }
        saveOffline(uuid, progress);
    }

    public static synchronized void setXP(ServerPlayer player, String clazz, int amount) {
        if (player == null || clazz == null || clazz.isBlank()) return;

        String normalizedClass = normalizeClass(clazz);
        String key = getPlayerKey(player);
        PlayerProgress progress = getOrLoad(player, key);
        PROGRESS_SERVICE.setExperience(progress, normalizedClass, amount, PROGRESSION_RULES);
        markDirty(key);
    }

    public static synchronized int getLevel(ServerPlayer player, String clazz) {
        String normalizedClass = normalizeClass(clazz);

        if (MapSetManager.isCompetitiveSet()) {
            return isBaseProgressionClass(normalizedClass) ? EPIC_TIER : BASIC_TIER;
        }

        if (isShopClass(normalizedClass)) {
            return isClassPurchased(player, normalizedClass) ? getShopFixedLevel(normalizedClass) : BASIC_TIER;
        }

        if (!isBaseClassUnlocked(player, normalizedClass)) {
            return BASIC_TIER;
        }

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));
        return getStoredTier(progress, normalizedClass);
    }

    public static int getLevelForXP(int xp) {
        return ProgressPolicy.calculateLevel(xp, PROGRESSION_RULES);
    }

    public static synchronized boolean isBaseClassUnlocked(ServerPlayer player, String clazz) {
        if (player == null || clazz == null) return false;

        String normalizedClass = normalizeClass(clazz);
        if (!isBaseProgressionClass(normalizedClass)) return false;
        if (MapSetManager.isCompetitiveSet()) return true;

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));
        return isBaseClassUnlocked(progress, normalizedClass);
    }

    public static synchronized boolean canGainXp(ServerPlayer player, String clazz) {
        if (player == null || clazz == null) return false;
        if (MapSetManager.isCompetitiveSet()) return false;
        String normalizedClass = normalizeClass(clazz);
        return isBaseProgressionClass(normalizedClass) && isBaseClassUnlocked(player, normalizedClass);
    }

    public static synchronized ProgressionResult unlockBaseClass(ServerPlayer player, String clazz) {
        if (player == null || clazz == null) return ProgressionResult.INVALID_CLASS;
        if (MapSetManager.isCompetitiveSet()) return ProgressionResult.ALREADY_UNLOCKED;

        String normalizedClass = normalizeClass(clazz);
        if (!isUnlockableBaseClass(normalizedClass)) {
            return ProgressionResult.INVALID_CLASS;
        }

        String key = getPlayerKey(player);
        PlayerProgress progress = getOrLoad(player, key);
        BaseUnlockResult result = PROGRESS_SERVICE.unlockBaseClass(
                progress, normalizedClass, progressContext());
        if (!result.changed()) return mapProgressionStatus(result.status());

        markDirty(key);
        return mapProgressionStatus(result.status());
    }

    public static synchronized ProgressionResult upgradeClassTier(ServerPlayer player, String clazz, int targetTier) {
        if (player == null || clazz == null) return ProgressionResult.INVALID_CLASS;
        if (MapSetManager.isCompetitiveSet()) return ProgressionResult.WRONG_TIER;

        String normalizedClass = normalizeClass(clazz);
        if (!isBaseProgressionClass(normalizedClass)) {
            return ProgressionResult.INVALID_CLASS;
        }

        String key = getPlayerKey(player);
        PlayerProgress progress = getOrLoad(player, key);
        TierUpgradeResult result = PROGRESS_SERVICE.upgradeTier(
                progress, normalizedClass, targetTier, progressContext());
        if (!result.changed()) return mapProgressionStatus(result.status());

        markDirty(key);
        return mapProgressionStatus(result.status());
    }

    public static synchronized void addCoins(ServerPlayer player, int amount) {
        if (player == null || amount == 0) return;

        String key = getPlayerKey(player);
        PlayerProgress progress = getOrLoad(player, key);
        PROGRESS_SERVICE.addCoins(progress, amount);
        markDirty(key);
    }

    public static synchronized boolean addCoins(MinecraftServer server, UUID uuid, int amount) {
        if (server == null || uuid == null || amount == 0) return false;
        return addCoins(server, uuid, "", amount);
    }

    public static synchronized boolean addCoins(MinecraftServer server, UUID uuid, String lastKnownName, int amount) {
        if (server == null || uuid == null || amount == 0) return false;

        PlayerProgress progress = getOrLoad(server, uuid, lastKnownName);
        PROGRESS_SERVICE.addCoins(progress, amount);
        return saveOffline(uuid, progress);
    }

    public static synchronized RepositoryResult applyIdempotentCoinCredit(
            MinecraftServer server, UUID uuid, String lastKnownName, int amount, String idempotencyKey) {
        if (server == null || uuid == null) return RepositoryResult.failed("Missing coin credit context", null);
        PlayerProgress progress = getOrLoad(server, uuid, lastKnownName);
        normalize(progress);
        IdempotentCreditResult credit = PROGRESS_SERVICE.applyIdempotentCredit(
                progress, idempotencyKey, "set_reward", amount, Clock.systemUTC());
        RepositoryResult result = mapCreditResult(credit);
        if (credit.status() == IdempotentCreditStatus.APPLIED && !saveOffline(uuid, progress)) {
            credit.rollback().ifPresent(rollback -> PROGRESS_SERVICE.rollbackIdempotentCredit(progress, rollback));
            return RepositoryResult.failed("Failed to persist coin credit receipt", null);
        }
        if (result.status() == RepositoryResult.Status.APPLIED
                || result.status() == RepositoryResult.Status.ALREADY_APPLIED) {
            ServerPlayer online = server.getPlayerList().getPlayer(uuid);
            if (online != null) ClassXPManager.sync(online);
        }
        return result;
    }

    static RepositoryResult mapCreditResult(IdempotentCreditResult result) {
        return switch (result.status()) {
            case APPLIED -> RepositoryResult.applied();
            case ALREADY_APPLIED -> RepositoryResult.alreadyApplied();
            case CONFLICT -> RepositoryResult.conflict(result.diagnostic());
            case FAILED -> RepositoryResult.failed(result.diagnostic(), null);
        };
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
        PROGRESS_SERVICE.setCoins(progress, amount);
        markDirty(key);
    }

    public static synchronized int getCoins(MinecraftServer server, UUID uuid, String lastKnownName) {
        if (server == null || uuid == null) return 0;
        PlayerProgress progress = getOrLoad(server, uuid, lastKnownName);
        return progress.coins;
    }

    public static synchronized int getCoins(MinecraftServer server, UUID uuid) {
        return getCoins(server, uuid, "");
    }

    public static synchronized boolean setCoins(MinecraftServer server, UUID uuid, String lastKnownName, int amount) {
        if (server == null || uuid == null) return false;

        PlayerProgress progress = getOrLoad(server, uuid, lastKnownName);
        PROGRESS_SERVICE.setCoins(progress, amount);
        return saveOffline(uuid, progress);
    }

    public static synchronized RepositoryResult applyTransactionDebit(
            MinecraftServer server,
            CreateClanTransaction transaction
    ) {
        if (server == null || transaction == null || transaction.playerUuid() == null) {
            return RepositoryResult.failed("Missing player transaction context", null);
        }

        PlayerProgress progress = getOrLoad(server, transaction.playerUuid(), transaction.playerName());
        normalize(progress);
        return PlayerTransactionReceiptLedger.applyDebit(
                progress,
                transaction,
                Clock.systemUTC(),
                () -> saveOffline(transaction.playerUuid(), progress)
        );
    }

    public static synchronized RepositoryResult verifyTransactionDebit(
            MinecraftServer server,
            CreateClanTransaction transaction
    ) {
        if (server == null || transaction == null || transaction.playerUuid() == null) {
            return RepositoryResult.failed("Missing player transaction context", null);
        }
        PlayerProgress progress = getOrLoad(server, transaction.playerUuid(), transaction.playerName());
        normalize(progress);
        return PlayerTransactionReceiptLedger.verifyDebit(progress, transaction);
    }

    public static synchronized void updateLastKnownName(MinecraftServer server, UUID uuid, String name) {
        if (server == null || uuid == null || name == null || name.isBlank()) return;

        PlayerProgress progress = getOrLoad(server, uuid, name);
        if (!Objects.equals(progress.name, name)) {
            progress.name = name;
            saveOffline(uuid, progress);
        }
    }

    public static synchronized Optional<KnownPlayer> findKnownPlayerByUuid(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) return Optional.empty();

        String key = compactUuid(uuid);
        PlayerProgress progress = getOrLoadIfExists(server, uuid);
        if (progress == null) {
            return Optional.empty();
        }

        return Optional.of(new KnownPlayer(uuid, progress.name == null || progress.name.isBlank() ? uuid.toString() : progress.name));
    }

    public static synchronized Optional<KnownPlayer> findKnownPlayerByName(MinecraftServer server, String name) {
        if (server == null || name == null || name.isBlank()) return Optional.empty();
        init(server);

        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, PlayerProgress> entry : cache.entrySet()) {
            PlayerProgress progress = entry.getValue();
            if (progress == null || progress.name == null) continue;
            if (progress.name.toLowerCase(Locale.ROOT).equals(normalized)) {
                UUID uuid = parseProgressUuid(entry.getKey(), progress);
                if (uuid != null) {
                    return Optional.of(new KnownPlayer(uuid, progress.name));
                }
            }
        }

        if (playersRoot == null || !Files.isDirectory(playersRoot)) {
            return Optional.empty();
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(playersRoot, "*.json")) {
            for (Path file : stream) {
                PlayerProgress progress = readProgressFile(file);
                if (progress == null || progress.name == null) continue;
                if (!progress.name.toLowerCase(Locale.ROOT).equals(normalized)) continue;

                String key = stripJsonExtension(file.getFileName().toString());
                UUID uuid = parseProgressUuid(key, progress);
                if (uuid == null) continue;

                normalize(progress);
                cache.putIfAbsent(key, progress);
                return Optional.of(new KnownPlayer(uuid, progress.name));
            }
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to scan Tactical Tablet progress players", exception);
        }

        return Optional.empty();
    }

    public static synchronized void addMatchPlayed(ServerPlayer player) {
        if (player == null) return;

        String key = getPlayerKey(player);
        PlayerProgress progress = getOrLoad(player, key);
        PROGRESS_SERVICE.incrementCounter(progress, MutableProgressState.Counter.MATCHES_PLAYED, 1);
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
        PROGRESS_SERVICE.incrementCounter(progress, MutableProgressState.Counter.WINS, 1);
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
        PROGRESS_SERVICE.incrementCounter(progress, MutableProgressState.Counter.KILLS, 1);
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
        PROGRESS_SERVICE.incrementCounter(progress, MutableProgressState.Counter.DEATHS, 1);
        markDirty(key);
    }

    public static synchronized int getDeaths(ServerPlayer player) {
        if (player == null) return 0;

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));
        return progress.deaths;
    }

    public static synchronized boolean isClassPurchased(ServerPlayer player, String clazz) {
        if (player == null || clazz == null) return false;
        if (MapSetManager.isCompetitiveSet()) return false;

        String normalizedClass = normalizeClass(clazz);
        if (!isShopClass(normalizedClass)) return false;

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));
        return progress.purchasedClasses.getOrDefault(normalizedClass, 0) > 0;
    }

    public static synchronized PurchaseResult purchaseClass(ServerPlayer player, String clazz) {
        if (player == null || clazz == null) return PurchaseResult.NOT_PURCHASABLE;
        if (MapSetManager.isCompetitiveSet()) return PurchaseResult.NOT_PURCHASABLE;

        String normalizedClass = normalizeClass(clazz);
        if (getShopPrice(normalizedClass) <= 0) return PurchaseResult.NOT_PURCHASABLE;

        String key = getPlayerKey(player);
        PlayerProgress progress = getOrLoad(player, key);
        ProgressPurchaseResult purchase = PROGRESS_SERVICE.purchaseClass(
                progress,
                normalizedClass,
                progressContext()
        );
        PurchaseResult legacyResult = mapPurchaseResult(purchase);
        if (!purchase.successful()) return legacyResult;

        markDirty(key);
        return legacyResult;
    }

    static PurchaseResult mapPurchaseResult(ProgressPurchaseResult result) {
        if (result.successful()) return PurchaseResult.PURCHASED;
        return switch (result.failure()) {
            case ALREADY_OWNED -> PurchaseResult.ALREADY_OWNED;
            case INSUFFICIENT_FUNDS -> PurchaseResult.NOT_ENOUGH_COINS;
            case INVALID_ITEM -> PurchaseResult.NOT_PURCHASABLE;
            case NONE -> throw new IllegalArgumentException("Rejected purchase cannot have NONE failure");
        };
    }

    static ProgressionResult mapProgressionStatus(ProgressionStatus status) {
        return switch (status) {
            case SUCCESS -> ProgressionResult.SUCCESS;
            case ALREADY_UNLOCKED -> ProgressionResult.ALREADY_UNLOCKED;
            case LOCKED -> ProgressionResult.LOCKED;
            case NOT_ENOUGH_COINS -> ProgressionResult.NOT_ENOUGH_COINS;
            case NOT_ENOUGH_XP -> ProgressionResult.NOT_ENOUGH_XP;
            case INVALID_CLASS -> ProgressionResult.INVALID_CLASS;
            case MAX_TIER -> ProgressionResult.MAX_TIER;
            case WRONG_TIER -> ProgressionResult.WRONG_TIER;
        };
    }

    private static ProgressContext progressContext() {
        return new ProgressContext(MapSetManager.isCompetitiveSet());
    }

    public static synchronized boolean isExclusiveClassGranted(ServerPlayer player, String clazz) {
        if (player == null || clazz == null) return false;
        return isExclusiveClassGranted(player.server, player.getUUID(), player.getGameProfile().getName(), clazz);
    }

    public static synchronized boolean grantExclusiveClass(ServerPlayer player, String clazz) {
        if (player == null || clazz == null) return false;

        return grantExclusiveClass(player.server, player.getUUID(), player.getGameProfile().getName(), clazz)
                == ExclusiveClassGrantResult.GRANTED;
    }

    public static synchronized boolean isExclusiveClassGranted(
            MinecraftServer server,
            UUID uuid,
            String lastKnownName,
            String clazz
    ) {
        if (server == null || uuid == null || clazz == null) return false;

        String normalizedClass = normalizeClass(clazz);
        if (!isExclusiveClass(normalizedClass)) return false;

        PlayerProgress progress = getOrLoad(server, uuid, lastKnownName);
        return progress.purchasedClasses.getOrDefault(normalizedClass, 0) > 0;
    }

    public static synchronized ExclusiveClassGrantResult grantExclusiveClass(
            MinecraftServer server,
            UUID uuid,
            String lastKnownName,
            String clazz
    ) {
        if (server == null || uuid == null || clazz == null) {
            return ExclusiveClassGrantResult.INVALID_CLASS;
        }

        PlayerProgress progress = getOrLoad(server, uuid, lastKnownName);
        ExclusiveUnlockResult result = PROGRESS_SERVICE.grantExclusiveClass(progress, clazz);
        if (!result.changed()) return mapExclusiveUnlockResult(result);
        if (saveOffline(uuid, progress)) return mapExclusiveUnlockResult(result);

        result.rollback().ifPresent(rollback -> PROGRESS_SERVICE.rollbackExclusiveClass(progress, rollback));
        return ExclusiveClassGrantResult.SAVE_FAILED;
    }

    static ExclusiveClassGrantResult grantExclusiveClassForPersistence(
            Map<String, Integer> purchasedClasses,
            Map<String, Integer> classes,
            String clazz,
            BooleanSupplier save
    ) {
        if (purchasedClasses == null || classes == null || save == null) {
            return ExclusiveClassGrantResult.INVALID_CLASS;
        }
        ExclusiveUnlockState state = exclusiveUnlockState(purchasedClasses, classes);
        ExclusiveUnlockResult result = PROGRESS_SERVICE.grantExclusiveClass(state, clazz);
        if (!result.changed()) return mapExclusiveUnlockResult(result);
        if (save.getAsBoolean()) return mapExclusiveUnlockResult(result);

        result.rollback().ifPresent(rollback -> PROGRESS_SERVICE.rollbackExclusiveClass(state, rollback));
        return ExclusiveClassGrantResult.SAVE_FAILED;
    }

    static ExclusiveClassGrantResult mapExclusiveUnlockResult(ExclusiveUnlockResult result) {
        return switch (result.status()) {
            case GRANTED -> ExclusiveClassGrantResult.GRANTED;
            case ALREADY_OWNED -> ExclusiveClassGrantResult.ALREADY_OWNED;
            case INVALID_CLASS -> ExclusiveClassGrantResult.INVALID_CLASS;
        };
    }

    private static ExclusiveUnlockState exclusiveUnlockState(
            Map<String, Integer> purchasedClasses,
            Map<String, Integer> classes
    ) {
        return new ExclusiveUnlockState() {
            public ProgressEntry experience(String classId) { return ProgressEntry.from(classes.get(classId)); }
            public void experience(String classId, int value) { classes.put(classId, value); }
            public void removeExperience(String classId) { classes.remove(classId); }
            public ProgressEntry purchase(String classId) { return ProgressEntry.from(purchasedClasses.get(classId)); }
            public void purchase(String classId, int value) { purchasedClasses.put(classId, value); }
            public void removePurchase(String classId) { purchasedClasses.remove(classId); }
        };
    }
    public static synchronized Map<String, Integer> getPurchasedClasses(ServerPlayer player) {
        Map<String, Integer> result = new HashMap<>();
        if (player == null) return result;

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));

        for (String clazz : SHOP_CLASSES) {
            String normalizedClass = normalizeClass(clazz);
            result.put(normalizedClass, MapSetManager.isCompetitiveSet()
                    ? 0
                    : progress.purchasedClasses.getOrDefault(normalizedClass, 0) > 0 ? 1 : 0);
        }

        for (String clazz : EXCLUSIVE_CLASSES) {
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
            result.put(normalizedClass, MapSetManager.isCompetitiveSet()
                    || isBaseClassUnlocked(progress, normalizedClass) ? 1 : 0);
        }
        return result;
    }

    public static synchronized Map<String, Integer> getClassTiers(ServerPlayer player) {
        Map<String, Integer> result = new HashMap<>();
        if (player == null) return result;

        PlayerProgress progress = getOrLoad(player, getPlayerKey(player));
        for (String clazz : BASE_CLASSES) {
            String normalizedClass = normalizeClass(clazz);
            result.put(normalizedClass, MapSetManager.isCompetitiveSet()
                    ? EPIC_TIER
                    : getStoredTier(progress, normalizedClass));
        }
        for (String clazz : SHOP_CLASSES) {
            String normalizedClass = normalizeClass(clazz);
            result.put(normalizedClass, MapSetManager.isCompetitiveSet()
                    ? BASIC_TIER
                    : isClassPurchased(player, normalizedClass) ? getShopFixedLevel(normalizedClass) : BASIC_TIER);
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
            completed += normalizePersistedXp(xp) / (double) MAX_CLASS_XP;
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
        PROGRESS_SERVICE.incrementCounter(progress, MutableProgressState.Counter.BATTLE_PASS_XP, amount);
        markDirty(key);
    }

    public static synchronized void tick(MinecraftServer server) {
        if (server == null || dataRoot == null || persistenceExecutor == null) return;

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
            int xp = isShopClass(normalizedClass)
                    ? 0
                    : MapSetManager.isCompetitiveSet() ? ClassTier.EPIC.requiredXp() : progress.classes.getOrDefault(normalizedClass, 0);
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
            int level = MapSetManager.isCompetitiveSet()
                    ? isBaseProgressionClass(normalizedClass) ? EPIC_TIER : BASIC_TIER
                    : isShopClass(normalizedClass)
                            ? progress.purchasedClasses.getOrDefault(normalizedClass, 0) > 0 ? getShopFixedLevel(normalizedClass) : BASIC_TIER
                            : getStoredTier(progress, normalizedClass);
            result.put(normalizedClass, level);
        }

        return result;
    }

    public static synchronized void backupNow() {
        if (playersRoot == null || backupsRoot == null || persistenceExecutor == null) return;
        if (!backupCoordinator.tryStart()) return;

        List<ProgressSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<String, PlayerProgress> entry : cache.entrySet()) {
            PlayerProgress progress = entry.getValue();
            progress.lastSeen = Instant.now().toEpochMilli();
            normalize(progress);
            snapshots.add(snapshot(entry.getKey(), progress, ++nextSnapshotRevision));
            enqueueSnapshot(entry.getKey(), progress, snapshots.get(snapshots.size() - 1));
        }

        BackupSnapshot backup = new BackupSnapshot(
                dataRoot,
                playersRoot,
                backupsRoot,
                BACKUP_FORMAT.format(Instant.now()) + "_" + (nextBackupRevision + 1),
                List.copyOf(snapshots),
                ++nextBackupRevision
        );
        ModPersistenceExecutor.SubmitResult result = persistenceExecutor.submit(new BackupWriteTask(backup));
        if (result.status() == ModPersistenceExecutor.SubmitStatus.BACKPRESSURED
                || result.status() == ModPersistenceExecutor.SubmitStatus.CLOSED) {
            backupCoordinator.finish();
            TacticalTabletMod.LOGGER.warn("Could not queue Tactical Tablet progress backup: {}", result.diagnostic());
        }
    }

    /** Initializes synchronous migration/loading before the first tick can schedule persistence. */
    public static synchronized void onServerStarted(MinecraftServer server) {
        init(server);
    }

    /** Queues final immutable snapshots, then waits only a bounded time during server shutdown. */
    public static synchronized void flushForShutdown() {
        if (persistenceExecutor == null) return;
        persistenceExecutor.stopAccepting();
        saveAll(true);
        boolean completed = persistenceExecutor.flush(SHUTDOWN_FLUSH_TIMEOUT);
        if (!completed) {
            TacticalTabletMod.LOGGER.error("Timed out after {} ms while flushing Tactical Tablet progress persistence; {} target(s) remain queued",
                    SHUTDOWN_FLUSH_TIMEOUT.toMillis(), persistenceExecutor.pendingTargets());
        }
        persistenceExecutor.close();
    }

    public static synchronized Path getDataRoot() {
        return dataRoot;
    }

    private static PlayerProgress getOrLoad(MinecraftServer server, UUID uuid, String lastKnownName) {
        init(server);

        PlayerProgress cached = findCachedByUuid(uuid);
        if (cached != null) {
            updateIdentity(cached, uuid, lastKnownName);
            normalize(cached);
            return cached;
        }

        LoadedProgress loaded = getOrLoadIfExistsWithKey(server, uuid);
        PlayerProgress progress = loaded == null ? null : loaded.progress;
        if (progress == null) {
            progress = createProgress(uuid, lastKnownName);
        }
        updateIdentity(progress, uuid, lastKnownName);
        normalize(progress);
        cache.put(getPlayerKey(progress, uuid), progress);
        return progress;
    }

    private static PlayerProgress getOrLoadIfExists(MinecraftServer server, UUID uuid) {
        LoadedProgress loaded = getOrLoadIfExistsWithKey(server, uuid);
        return loaded == null ? null : loaded.progress;
    }

    private static LoadedProgress getOrLoadIfExistsWithKey(MinecraftServer server, UUID uuid) {
        init(server);

        PlayerProgress cached = findCachedByUuid(uuid);
        if (cached != null) {
            normalize(cached);
            return new LoadedProgress(getPlayerKey(cached, uuid), cached);
        }

        String legacyKey = compactUuid(uuid);
        Path legacyFile = getPlayerFile(legacyKey);
        if (Files.exists(legacyFile)) {
            PlayerProgress progress = readProgressFile(legacyFile);
            if (progress != null) {
                normalize(progress);
                String key = getPlayerKey(progress, uuid);
                cache.put(key, progress);
                return new LoadedProgress(key, progress);
            }
        }

        if (playersRoot == null || !Files.isDirectory(playersRoot)) {
            return null;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(playersRoot, "*.json")) {
            for (Path file : stream) {
                PlayerProgress progress = readProgressFile(file);
                if (progress == null) continue;

                UUID progressUuid = parseProgressUuid(stripJsonExtension(file.getFileName().toString()), progress);
                if (!uuid.equals(progressUuid)) continue;

                normalize(progress);
                String key = getPlayerKey(progress, uuid);
                cache.put(key, progress);
                return new LoadedProgress(key, progress);
            }
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to scan Tactical Tablet progress players", exception);
        }

        return null;
    }

    private static PlayerProgress getOrLoad(ServerPlayer player, String key) {
        init(player.server);

        PlayerProgress cached = cache.get(key);
        if (cached != null) {
            updateIdentity(cached, player);
            return cached;
        }

        cached = findCachedByUuid(player.getUUID());
        if (cached != null) {
            updateIdentity(cached, player);
            normalize(cached);
            cache.put(getPlayerKey(cached, player.getUUID()), cached);
            return cached;
        }

        PlayerProgress progress = readOrCreateProgress(player, key);
        updateIdentity(progress, player);
        normalize(progress);
        cache.put(getPlayerKey(progress, player.getUUID()), progress);
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
                        "Migrating Tactical Tablet progress for {} from UUID key {} to name key {}",
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

    private static PlayerProgress createProgress(UUID uuid, String lastKnownName) {
        PlayerProgress progress = new PlayerProgress();
        progress.name = lastKnownName == null || lastKnownName.isBlank() ? uuid.toString() : lastKnownName;
        progress.uuid = compactUuid(uuid);
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

    private static boolean updateIdentity(PlayerProgress progress, UUID uuid, String lastKnownName) {
        boolean changed = false;
        String compactUuid = compactUuid(uuid);
        String safeName = lastKnownName == null || lastKnownName.isBlank()
                ? progress.name == null || progress.name.isBlank() ? uuid.toString() : progress.name
                : lastKnownName;

        if (!Objects.equals(progress.uuid, compactUuid)) {
            progress.uuid = compactUuid;
            changed = true;
        }

        if (!Objects.equals(progress.name, safeName)) {
            progress.name = safeName;
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
        persistenceExecutor = new ModPersistenceExecutor(
                "TacticalTablet-Persistence",
                PERSISTENCE_QUEUE_LIMIT,
                message -> TacticalTabletMod.LOGGER.warn("{}", message)
        );

        try {
            Files.createDirectories(playersRoot);
            Files.createDirectories(backupsRoot);
            migratePlayerFilesToNameKeys();
            ensureSeasonFile();
        } catch (IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to initialize Tactical Tablet progress storage", exception);
        }
    }

    private static void migratePlayerFilesToNameKeys() throws IOException {
        if (!Files.isDirectory(playersRoot)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(playersRoot, "*.json")) {
            for (Path source : stream) {
                String sourceKey = stripJsonExtension(source.getFileName().toString());
                PlayerProgress progress = readProgressFile(source);
                if (progress == null) continue;

                UUID uuid = parseProgressUuid(sourceKey, progress);
                if (uuid == null) continue;

                updateIdentity(progress, uuid, progress.name);
                normalize(progress);

                String targetKey = getPlayerKey(progress, uuid);
                if (targetKey.isBlank() || targetKey.equals(sourceKey)) continue;

                Path target = getPlayerFile(targetKey);
                if (Files.exists(target)) {
                    TacticalTabletMod.LOGGER.warn(
                            "Cannot migrate Tactical Tablet progress file {} to {} because target already exists",
                            source.getFileName(),
                            target.getFileName()
                    );
                    continue;
                }

                writeJsonAtomically(target, progress);
                Files.deleteIfExists(source);
                TacticalTabletMod.LOGGER.info(
                        "Migrated Tactical Tablet progress file {} to {}",
                        source.getFileName(),
                        target.getFileName()
                );
            }
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
        reconcileCompletedWrites();
        if (dirty.isEmpty()) return;

        for (String key : dirty.keySet().toArray(new String[0])) {
            PlayerProgress progress = cache.get(key);
            if (progress == null) {
                dirty.remove(key);
                continue;
            }

            progress.lastSeen = Instant.now().toEpochMilli();
            normalize(progress);
            enqueueSnapshot(key, progress);
        }
    }

    private static SaveTicket enqueueSnapshot(String key, PlayerProgress progress) {
        return enqueueSnapshot(key, progress, snapshot(key, progress, ++nextSnapshotRevision), false);
    }

    private static SaveTicket enqueueSnapshot(String key, PlayerProgress progress, ProgressSnapshot snapshot) {
        return enqueueSnapshot(key, progress, snapshot, false);
    }

    private static SaveTicket enqueueSnapshot(String key, PlayerProgress progress, boolean finalSnapshot) {
        return enqueueSnapshot(key, progress, snapshot(key, progress, ++nextSnapshotRevision), finalSnapshot);
    }

    private static SaveTicket enqueueSnapshot(String key, PlayerProgress progress, ProgressSnapshot snapshot, boolean finalSnapshot) {
        if (persistenceExecutor == null || playersRoot == null) {
            markDirty(key);
            return null;
        }
        Path target = getPlayerFile(key);
        SaveTicket ticket = finalSnapshot
                ? persistenceExecutor.enqueueFinalSnapshot(new PlayerWriteTask(target, snapshot))
                : persistenceExecutor.enqueueSnapshot(new PlayerWriteTask(target, snapshot));
        DurableSaveResult immediate = ticket.completion().toCompletableFuture().getNow(null);
        if (immediate == null || immediate.status() == DurableSaveResult.Status.WRITTEN) {
            snapshotRevisions.put(key, snapshot.revision());
            markDirty(key);
            return ticket;
        }
        switch (immediate.status()) {
            case STALE_REJECTED, SUPERSEDED -> { }
            case QUEUE_REJECTED, EXECUTOR_STOPPED, FAILED -> {
                markDirty(key);
                TacticalTabletMod.LOGGER.warn("Could not queue Tactical Tablet progress save for {}: {}", key, immediate.diagnostic());
            }
            case WRITTEN -> { }
        }
        return ticket;
    }

    private static void reconcileCompletedWrites() {
        if (persistenceExecutor == null) return;
        for (String key : dirty.keySet().toArray(new String[0])) {
            Long revision = snapshotRevisions.get(key);
            if (revision == null) continue;
            if (persistenceExecutor.completedRevision(getPlayerFile(key)) >= revision) {
                dirty.remove(key);
            }
        }
    }

    private static ProgressSnapshot snapshot(String key, PlayerProgress progress, long revision) {
        return new ProgressSnapshot(key, revision, new ProgressSnapshot.Data(
                progress.dataVersion,
                progress.name,
                progress.uuid,
                Map.copyOf(progress.classes),
                Map.copyOf(progress.classTiers),
                Map.copyOf(progress.unlockedBaseClasses),
                progress.wins,
                progress.kills,
                progress.deaths,
                progress.matchesPlayed,
                progress.coins,
                progress.battlePassXp,
                progress.xpBoost,
                progress.sadTromboneKills,
                Map.copyOf(progress.purchasedClasses),
                Map.copyOf(progress.donations),
                Map.copyOf(progress.stats),
                List.copyOf(progress.appliedTransactionReceipts),
                progress.firstSeen,
                progress.lastSeen
        ));
    }

    private static void markDirty(String key) {
        dirty.put(key, Boolean.TRUE);
    }

    private static PlayerProgress findCachedByUuid(UUID uuid) {
        if (uuid == null) return null;
        String compact = compactUuid(uuid);

        for (PlayerProgress progress : cache.values()) {
            if (progress == null) continue;
            if (compact.equals(progress.uuid)) {
                return progress;
            }
        }

        return null;
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
        progress.coins = ProgressPolicy.normalizeCoins(progress.coins);
        progress.battlePassXp = Math.max(0, progress.battlePassXp);

        progress.classes = normalizeIntegerMap(progress.classes);
        progress.classTiers = normalizeIntegerMap(progress.classTiers);
        progress.unlockedBaseClasses = normalizeIntegerMap(progress.unlockedBaseClasses);
        progress.purchasedClasses = normalizeIntegerMap(progress.purchasedClasses);
        progress.donations = normalizeIntegerMap(progress.donations);
        progress.stats = normalizeIntegerMap(progress.stats);
        progress.appliedTransactionReceipts = PlayerTransactionReceiptLedger.normalizeReceipts(progress.appliedTransactionReceipts);

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

        progress.classes.replaceAll((clazz, xp) -> normalizePersistedXp(xp == null ? 0 : xp));
        progress.classTiers.replaceAll((clazz, tier) -> normalizePersistedTier(tier == null ? BASIC_TIER : tier));

        for (String clazz : BASE_CLASSES) {
            String normalizedClass = normalizeClass(clazz);
            progress.classes.put(normalizedClass, normalizePersistedXp(progress.classes.getOrDefault(normalizedClass, 0)));
            progress.classTiers.put(normalizedClass, clampTier(progress.classTiers.getOrDefault(normalizedClass, BASIC_TIER)));
            progress.unlockedBaseClasses.put(normalizedClass, isBaseClassUnlocked(progress, normalizedClass) ? 1 : 0);
        }

        for (String clazz : SHOP_CLASSES) {
            progress.purchasedClasses.putIfAbsent(normalizeClass(clazz), 0);
        }

        for (String clazz : EXCLUSIVE_CLASSES) {
            progress.purchasedClasses.putIfAbsent(normalizeClass(clazz), 0);
        }
    }

    private static boolean requiresProgressionNormalization(PlayerProgress progress) {
        if (progress == null || progress.classes == null || progress.classTiers == null) return true;
        for (Integer xp : progress.classes.values()) {
            if (xp == null || xp != normalizePersistedXp(xp)) return true;
        }
        for (Integer tier : progress.classTiers.values()) {
            if (tier == null || tier != normalizePersistedTier(tier)) return true;
        }
        for (String clazz : BASE_CLASSES) {
            String normalizedClass = normalizeClass(clazz);
            if (!progress.classes.containsKey(normalizedClass) || !progress.classTiers.containsKey(normalizedClass)) {
                return true;
            }
        }
        return false;
    }

    private static void migrateLegacyBaseProgress(PlayerProgress progress) {
        for (String clazz : BASE_CLASSES) {
            String normalizedClass = normalizeClass(clazz);
            int xp = normalizePersistedXp(progress.classes.getOrDefault(normalizedClass, 0));

            if (xp > 0 || isInitialBaseClass(normalizedClass)) {
                progress.unlockedBaseClasses.put(normalizedClass, 1);
            }

            if (!progress.classTiers.containsKey(normalizedClass)) {
                // Profiles without an explicit purchase record start at BASIC; do not infer purchases from XP.
                progress.classTiers.put(normalizedClass, BASIC_TIER);
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
        if (progress == null) return BASIC_TIER;
        return clampTier(progress.classTiers.getOrDefault(normalizeClass(clazz), BASIC_TIER));
    }

    private static int clampTier(int tier) {
        return normalizePersistedTier(tier);
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
        return ProgressPolicy.normalizeNonNegativeValues(input);
    }

    private static String normalizeClass(String clazz) {
        return clazz == null ? "" : clazz.trim().toLowerCase(Locale.ROOT);
    }

    private static String getPlayerKey(ServerPlayer player) {
        return getPlayerKey(player.getGameProfile().getName(), player.getUUID());
    }

    private static Path getLegacyPlayerFile(ServerPlayer player) {
        String legacyKey = compactUuid(player);
        if (legacyKey.isBlank()) return null;

        return getPlayerFile(legacyKey);
    }

    private static String getPlayerKey(PlayerProgress progress, UUID fallbackUuid) {
        String key = getPlayerKey(progress == null ? "" : progress.name, fallbackUuid);
        if (!key.isBlank()) return key;
        return fallbackUuid == null ? "" : compactUuid(fallbackUuid);
    }

    private static String getPlayerKey(String name, UUID fallbackUuid) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9_.-]", "_");

        if (normalized.isBlank()) {
            normalized = fallbackUuid == null ? "" : compactUuid(fallbackUuid);
        }

        return normalized;
    }

    private static String compactUuid(ServerPlayer player) {
        return player.getUUID().toString().replace("-", "");
    }

    private static String compactUuid(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    private static Path getPlayerFile(String key) {
        return playersRoot.resolve(key + ".json");
    }

    private static boolean saveOffline(UUID uuid, PlayerProgress progress) {
        if (uuid == null || progress == null) return false;

        progress.lastSeen = Instant.now().toEpochMilli();
        normalize(progress);

        String key = getPlayerKey(progress, uuid);
        if (key.isBlank()) return false;
        if (persistenceExecutor == null) {
            TacticalTabletMod.LOGGER.error("Cannot durably save Tactical Tablet progress for {}: persistence executor is unavailable", key);
            markDirty(key);
            return false;
        }

        ProgressSnapshot snapshot = snapshot(key, progress, ++nextSnapshotRevision);
        SaveTicket ticket = enqueueSnapshot(key, progress, snapshot);
        boolean saved = awaitTicket(ticket, key);
        if (saved) {
            dirty.remove(key);
            cache.put(key, progress);
            return true;
        }
        TacticalTabletMod.LOGGER.error("Failed to durably flush Tactical Tablet progress for {}", key);
        markDirty(key);
        return false;
    }

    private static boolean awaitTicket(SaveTicket ticket, String key) {
        if (ticket == null) return false;
        try {
            DurableSaveResult result = ticket.completion().toCompletableFuture()
                    .get(SHUTDOWN_FLUSH_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (result.status() == DurableSaveResult.Status.WRITTEN) return true;
            TacticalTabletMod.LOGGER.error("Durable player save {} revision {} ended as {}: {}", key, ticket.revision(), result.status(), result.diagnostic());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException exception) {
            TacticalTabletMod.LOGGER.error("Timed out or failed waiting for player save {} revision {}", key, ticket.revision(), exception);
        }
        return false;
    }

    private static PlayerProgress readProgressFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, PlayerProgress.class);
        } catch (JsonSyntaxException | IOException exception) {
            TacticalTabletMod.LOGGER.error("Failed to read Tactical Tablet progress file {}", file, exception);
            backupCorruptFile(file);
            return null;
        }
    }

    private static UUID parseProgressUuid(String key, PlayerProgress progress) {
        UUID uuid = parseCompactUuid(progress == null ? "" : progress.uuid);
        if (uuid != null) return uuid;
        return parseCompactUuid(key);
    }

    private static UUID parseCompactUuid(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        try {
            if (normalized.length() == 32 && normalized.indexOf('-') < 0) {
                normalized = normalized.substring(0, 8) + "-"
                        + normalized.substring(8, 12) + "-"
                        + normalized.substring(12, 16) + "-"
                        + normalized.substring(16, 20) + "-"
                        + normalized.substring(20);
            }
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String stripJsonExtension(String fileName) {
        return fileName != null && fileName.endsWith(".json")
                ? fileName.substring(0, fileName.length() - 5)
                : fileName;
    }

    private static void writeJsonAtomically(Path file, Object value) throws IOException {
        FileSaveResult result = FILE_STORE.write(file, writer -> GSON.toJson(value, writer));
        if (result.status() != FileSaveResult.Status.SUCCESS) {
            throw new IOException(result.diagnostic(), result.exception().orElse(null));
        }
    }

    static void copyJsonFiles(Path sourceRoot, Path targetRoot) throws IOException {
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
                    .filter(path -> !path.getFileName().toString().startsWith("."))
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
        return ProgressPolicy.saturatingAdd(current, amount);
    }

    private static final class PlayerWriteTask implements ModPersistenceExecutor.WriteTask {
        private final Path target;
        private final ProgressSnapshot snapshot;

        private PlayerWriteTask(Path target, ProgressSnapshot snapshot) {
            this.target = target;
            this.snapshot = snapshot;
        }

        @Override
        public Path target() {
            return target;
        }

        @Override
        public long revision() {
            return snapshot.revision();
        }

        @Override
        public FileSaveResult write() {
            return FILE_STORE.write(target, writer -> GSON.toJson(snapshot.data(), writer));
        }
    }

    private record BackupSnapshot(
            Path dataRoot,
            Path playersRoot,
            Path backupsRoot,
            String timestamp,
            List<ProgressSnapshot> players,
            long revision
    ) {
        BackupSnapshot {
            players = List.copyOf(players);
        }
    }

    private static final class BackupWriteTask implements ModPersistenceExecutor.WriteTask {
        private final BackupSnapshot snapshot;

        private BackupWriteTask(BackupSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public Path target() {
            return snapshot.backupsRoot().resolve(snapshot.timestamp());
        }

        @Override
        public long revision() {
            return snapshot.revision();
        }

        @Override
        public FileSaveResult write() {
            try {
                createBackup(snapshot);
                return FileSaveResult.success(target());
            } catch (IOException | RuntimeException exception) {
                return FileSaveResult.failure(target(), "Failed to create progress backup", exception);
            } finally {
                backupCoordinator.finish();
            }
        }
    }

    private static void createBackup(BackupSnapshot snapshot) throws IOException {
        Path completed = snapshot.backupsRoot().resolve(snapshot.timestamp());
        Path temporary = snapshot.backupsRoot().resolve("." + snapshot.timestamp() + ".incomplete");
        Path temporaryPlayers = temporary.resolve(PLAYERS_DIRECTORY);
        Files.createDirectories(temporaryPlayers);

        // Existing offline players are copied first; active players are then replaced by the agreed snapshots.
        copyJsonFiles(snapshot.playersRoot(), temporaryPlayers);
        List<String> files = new ArrayList<>();
        for (ProgressSnapshot player : snapshot.players()) {
            Path target = temporaryPlayers.resolve(player.key() + ".json");
            FileSaveResult result = FILE_STORE.write(target, writer -> GSON.toJson(player.data(), writer));
            if (result.status() != FileSaveResult.Status.SUCCESS) {
                throw new IOException(result.diagnostic(), result.exception().orElse(null));
            }
            files.add(PLAYERS_DIRECTORY + "/" + target.getFileName());
        }

        Path season = snapshot.dataRoot().resolve(SEASON_FILE);
        if (Files.isRegularFile(season)) {
            Files.copy(season, temporary.resolve(SEASON_FILE), StandardCopyOption.REPLACE_EXISTING);
            files.add(SEASON_FILE);
        }
        copyBackupFile(snapshot.dataRoot().resolve("clans.json"), temporary.resolve("clans.json"), "clans.json", files);
        copyBackupTree(snapshot.dataRoot().resolve("transactions"), temporary.resolve("transactions"), "transactions", files);
        copyBackupTree(snapshot.dataRoot().resolve("migrations"), temporary.resolve("migrations"), "migrations", files);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(temporaryPlayers, "*.json")) {
            for (Path file : stream) {
                String entry = PLAYERS_DIRECTORY + "/" + file.getFileName();
                if (!files.contains(entry)) files.add(entry);
            }
        }
        Map<String, Object> manifest = new HashMap<>();
        manifest.put("schemaVersion", DATA_VERSION);
        manifest.put("generationId", snapshot.revision());
        manifest.put("timestamp", snapshot.timestamp());
        manifest.put("files", List.copyOf(files));
        Map<String, Long> sizes = new HashMap<>();
        for (String file : files) sizes.put(file, Files.size(temporary.resolve(file)));
        manifest.put("sizes", Map.copyOf(sizes));
        FileSaveResult manifestWrite = FILE_STORE.write(temporary.resolve("manifest.json"), writer -> GSON.toJson(manifest, writer));
        if (manifestWrite.status() != FileSaveResult.Status.SUCCESS) {
            throw new IOException(manifestWrite.diagnostic(), manifestWrite.exception().orElse(null));
        }
        try {
            Files.move(temporary, completed, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, completed);
        }
        cleanOldBackups();
    }

    private static void copyBackupFile(Path source, Path target, String relative, List<String> files) throws IOException {
        if (!Files.isRegularFile(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(source) || excludedBackupFile(source)) return;
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        files.add(relative);
    }

    private static void copyBackupTree(Path sourceRoot, Path targetRoot, String prefix, List<String> files) throws IOException {
        if (!Files.isDirectory(sourceRoot, java.nio.file.LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(sourceRoot)) return;
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            for (Path source : stream.filter(path -> !path.equals(sourceRoot)).toList()) {
                if (!Files.isRegularFile(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(source) || excludedBackupFile(source)) continue;
                Path relative = sourceRoot.relativize(source);
                Path target = targetRoot.resolve(relative).normalize();
                if (!target.startsWith(targetRoot)) continue;
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                files.add(prefix + "/" + relative.toString().replace('\\', '/'));
            }
        }
    }

    private static boolean excludedBackupFile(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".tmp") || name.contains(".incomplete");
    }

    private static final class PlayerProgress implements PlayerTransactionReceiptLedger.State, MutableProgressState {
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
        private boolean xpBoost;
        private boolean sadTromboneKills;
        private Map<String, Integer> purchasedClasses = new HashMap<>();
        private Map<String, Integer> donations = new HashMap<>();
        private Map<String, Integer> stats = new HashMap<>();
        private List<AppliedTransactionReceipt> appliedTransactionReceipts = new ArrayList<>();
        private long firstSeen;
        private long lastSeen;

        @Override
        public int coins() {
            return coins;
        }

        @Override
        public void coins(int value) {
            coins = value;
        }

        @Override
        public List<AppliedTransactionReceipt> receipts() {
            return appliedTransactionReceipts;
        }

        @Override
        public void receipts(List<AppliedTransactionReceipt> value) {
            appliedTransactionReceipts = value == null ? new ArrayList<>() : value;
        }

        @Override
        public ProgressEntry experience(String classId) {
            return ProgressEntry.from(classes.get(classId));
        }

        @Override
        public void experience(String classId, int value) {
            classes.put(classId, value);
        }

        @Override
        public void removeExperience(String classId) {
            classes.remove(classId);
        }

        @Override
        public ProgressEntry tier(String classId) {
            return ProgressEntry.from(classTiers.get(classId));
        }

        @Override
        public void tier(String classId, int value) {
            classTiers.put(classId, value);
        }

        @Override
        public ProgressEntry baseUnlock(String classId) {
            return ProgressEntry.from(unlockedBaseClasses.get(classId));
        }

        @Override
        public void baseUnlock(String classId, int value) {
            unlockedBaseClasses.put(classId, value);
        }

        @Override
        public void removeBaseUnlock(String classId) {
            unlockedBaseClasses.remove(classId);
        }

        @Override
        public ProgressEntry purchase(String classId) {
            return ProgressEntry.from(purchasedClasses.get(classId));
        }

        @Override
        public void purchase(String classId, int value) {
            purchasedClasses.put(classId, value);
        }

        @Override
        public void removePurchase(String classId) {
            purchasedClasses.remove(classId);
        }

        @Override
        public int counter(MutableProgressState.Counter counter) {
            return switch (counter) {
                case WINS -> wins;
                case KILLS -> kills;
                case DEATHS -> deaths;
                case MATCHES_PLAYED -> matchesPlayed;
                case BATTLE_PASS_XP -> battlePassXp;
            };
        }

        @Override
        public void counter(MutableProgressState.Counter counter, int value) {
            switch (counter) {
                case WINS -> wins = value;
                case KILLS -> kills = value;
                case DEATHS -> deaths = value;
                case MATCHES_PLAYED -> matchesPlayed = value;
                case BATTLE_PASS_XP -> battlePassXp = value;
            }
        }

        @Override
        public boolean flag(MutableProgressState.Flag flag) {
            return switch (flag) {
                case XP_BOOST -> xpBoost;
                case SAD_TROMBONE_KILLS -> sadTromboneKills;
            };
        }

        @Override
        public void flag(MutableProgressState.Flag flag, boolean value) {
            switch (flag) {
                case XP_BOOST -> xpBoost = value;
                case SAD_TROMBONE_KILLS -> sadTromboneKills = value;
            }
        }

        @Override
        public Optional<ProgressReceipt> receipt(String receiptId) {
            if (receiptId == null) return Optional.empty();
            for (AppliedTransactionReceipt receipt : appliedTransactionReceipts) {
                if (receipt != null && receiptId.equals(receipt.transactionId)) {
                    return Optional.of(toProgressReceipt(receipt));
                }
            }
            return Optional.empty();
        }

        @Override
        public void addReceipt(ProgressReceipt receipt) {
            appliedTransactionReceipts.add(toAppliedReceipt(receipt));
        }

        @Override
        public boolean removeReceipt(ProgressReceipt receipt) {
            return appliedTransactionReceipts.removeIf(candidate -> candidate != null
                    && toProgressReceipt(candidate).equals(receipt));
        }

        private static ProgressReceipt toProgressReceipt(AppliedTransactionReceipt receipt) {
            return new ProgressReceipt(
                    receipt.transactionId,
                    receipt.operationType,
                    receipt.appliedAt,
                    receipt.expectedOldBalance,
                    receipt.newBalance,
                    receipt.payloadHash
            );
        }

        private static AppliedTransactionReceipt toAppliedReceipt(ProgressReceipt receipt) {
            AppliedTransactionReceipt applied = new AppliedTransactionReceipt();
            applied.transactionId = receipt.transactionId();
            applied.operationType = receipt.operationType();
            applied.appliedAt = receipt.appliedAt();
            applied.expectedOldBalance = receipt.expectedOldBalance();
            applied.newBalance = receipt.newBalance();
            applied.payloadHash = receipt.payloadHash();
            return applied;
        }
    }

    private record LoadedProgress(String key, PlayerProgress progress) {
    }

    public record KnownPlayer(UUID uuid, String name) {
    }
}
