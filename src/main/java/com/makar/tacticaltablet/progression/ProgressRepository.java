package com.makar.tacticaltablet.progression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.makar.tacticaltablet.storage.AtomicFileStore;
import com.makar.tacticaltablet.storage.BackupCoordinator;
import com.makar.tacticaltablet.storage.FileSaveResult;
import com.makar.tacticaltablet.storage.ModPersistenceExecutor;
import com.makar.tacticaltablet.storage.SaveTicket;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/** File storage boundary for immutable progression snapshots. */
public final class ProgressRepository implements AutoCloseable {
    static final String DATA_DIRECTORY = "tacticaltablet_data";
    static final String PLAYERS_DIRECTORY = "players";
    static final String BACKUPS_DIRECTORY = "backups";
    static final String SEASON_FILE = "season.json";
    static final int MAX_BACKUP_FOLDERS = 48;

    private static final int PERSISTENCE_QUEUE_LIMIT = 512;
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd_HH-mm-ss")
            .withZone(ZoneId.systemDefault());

    private final Path dataRoot;
    private final Path playersRoot;
    private final Path backupsRoot;
    private final Configuration configuration;
    private final Clock clock;
    private final AtomicFileStore fileStore;
    private final RepositoryLog log;
    private final BackupCoordinator backupCoordinator = new BackupCoordinator();
    private final ModPersistenceExecutor executor;

    ProgressRepository(Path serverRoot, Configuration configuration, RepositoryLog log) {
        this(serverRoot, configuration, Clock.systemUTC(), new AtomicFileStore(), log, PERSISTENCE_QUEUE_LIMIT);
    }

    ProgressRepository(
            Path serverRoot,
            Configuration configuration,
            Clock clock,
            AtomicFileStore fileStore,
            RepositoryLog log,
            int queueLimit
    ) {
        Path normalizedRoot = Objects.requireNonNull(serverRoot, "serverRoot").toAbsolutePath().normalize();
        this.dataRoot = normalizedRoot.resolve(DATA_DIRECTORY);
        this.playersRoot = dataRoot.resolve(PLAYERS_DIRECTORY);
        this.backupsRoot = dataRoot.resolve(BACKUPS_DIRECTORY);
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.fileStore = Objects.requireNonNull(fileStore, "fileStore");
        this.log = log == null ? RepositoryLog.noop() : log;
        this.executor = new ModPersistenceExecutor(
                "TacticalTablet-Persistence",
                queueLimit,
                message -> this.log.warn(message)
        );
    }

    void initialize() {
        try {
            Files.createDirectories(playersRoot);
            Files.createDirectories(backupsRoot);
            migratePlayerFilesToNameKeys();
            ensureSeasonFile();
        } catch (IOException exception) {
            log.error("Failed to initialize Tactical Tablet progress storage", exception);
        }
    }

    Optional<LoadedProfile> loadByKey(String key) {
        if (key == null || key.isBlank()) return Optional.empty();
        return readProfile(playerFile(key), key);
    }

    Optional<LoadedProfile> findByUuid(UUID uuid) {
        if (uuid == null) return Optional.empty();
        Optional<LoadedProfile> legacy = loadByKey(compactUuid(uuid));
        if (legacy.isPresent()) return legacy;

        for (LoadedProfile profile : loadAll()) {
            UUID profileUuid = parseProgressUuid(profile.sourceKey(), profile.data());
            if (uuid.equals(profileUuid)) return Optional.of(profile);
        }
        return Optional.empty();
    }

    List<LoadedProfile> loadAll() {
        try {
            return scanProfiles();
        } catch (IOException exception) {
            log.error("Failed to scan Tactical Tablet progress players", exception);
            return List.of();
        }
    }

    boolean exists(String key) {
        return key != null && !key.isBlank() && Files.exists(playerFile(key));
    }

    SaveTicket save(ProgressSnapshot snapshot, boolean finalSnapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        PlayerWriteTask task = new PlayerWriteTask(playerFile(snapshot.key()), snapshot);
        return finalSnapshot ? executor.enqueueFinalSnapshot(task) : executor.enqueueSnapshot(task);
    }

    long completedRevision(String key) {
        return executor.completedRevision(playerFile(key));
    }

    ModPersistenceExecutor.SubmitResult backup(
            List<ProgressSnapshot> players,
            long revision
    ) {
        Objects.requireNonNull(players, "players");
        if (!backupCoordinator.isRunning() && !backupCoordinator.tryStart()) {
            Path target = backupsRoot.resolve("backup-in-progress");
            return new ModPersistenceExecutor.SubmitResult(
                    ModPersistenceExecutor.SubmitStatus.COALESCED,
                    target,
                    revision,
                    "A progress backup is already running"
            );
        }
        BackupSnapshot snapshot = new BackupSnapshot(
                BACKUP_FORMAT.format(clock.instant()) + "_" + revision,
                players,
                revision
        );
        ModPersistenceExecutor.SubmitResult result = executor.submit(new BackupWriteTask(snapshot));
        if (result.status() == ModPersistenceExecutor.SubmitStatus.BACKPRESSURED
                || result.status() == ModPersistenceExecutor.SubmitStatus.CLOSED) {
            backupCoordinator.finish();
        }
        return result;
    }

    boolean tryStartBackup() {
        return backupCoordinator.tryStart();
    }

    void stopAccepting() {
        executor.stopAccepting();
    }

    boolean flush(java.time.Duration timeout) {
        return executor.flush(timeout);
    }

    int pendingTargets() {
        return executor.pendingTargets();
    }

    Path dataRoot() {
        return dataRoot;
    }

    Path playersRoot() {
        return playersRoot;
    }

    Path backupsRoot() {
        return backupsRoot;
    }

    Path playerFile(String key) {
        return playersRoot.resolve(key + ".json");
    }

    @Override
    public void close() {
        executor.close();
    }

    private Optional<LoadedProfile> readProfile(Path file, String sourceKey) {
        if (!Files.exists(file)) return Optional.empty();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            StoredProgress stored = GSON.fromJson(reader, StoredProgress.class);
            if (stored == null) return Optional.empty();
            int oldVersion = stored.dataVersion;
            boolean progressionValuesInvalid = requiresProgressionNormalization(stored);
            normalize(stored);
            return Optional.of(new LoadedProfile(
                    sourceKey,
                    toData(stored),
                    oldVersion < configuration.dataVersion() || progressionValuesInvalid
            ));
        } catch (JsonSyntaxException | IOException exception) {
            log.error("Failed to read Tactical Tablet progress file " + file, exception);
            backupCorruptFile(file);
            return Optional.empty();
        }
    }

    private void migratePlayerFilesToNameKeys() throws IOException {
        if (!Files.isDirectory(playersRoot)) return;
        for (LoadedProfile loaded : scanProfiles()) {
            UUID uuid = parseProgressUuid(loaded.sourceKey(), loaded.data());
            if (uuid == null) continue;

            StoredProgress stored = fromData(loaded.data());
            updateIdentity(stored, uuid, stored.name);
            normalize(stored);
            String targetKey = storageKey(stored.name, uuid);
            if (targetKey.isBlank() || targetKey.equals(loaded.sourceKey())) continue;

            Path source = playerFile(loaded.sourceKey());
            Path target = playerFile(targetKey);
            if (Files.exists(target)) {
                log.warn("Cannot migrate Tactical Tablet progress file " + source.getFileName()
                        + " to " + target.getFileName() + " because target already exists");
                continue;
            }

            writeJsonAtomically(target, toData(stored));
            Files.deleteIfExists(source);
            log.info("Migrated Tactical Tablet progress file " + source.getFileName()
                    + " to " + target.getFileName());
        }
    }

    private List<LoadedProfile> scanProfiles() throws IOException {
        if (!Files.isDirectory(playersRoot)) return List.of();
        List<LoadedProfile> profiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(playersRoot, "*.json")) {
            for (Path file : stream) {
                String sourceKey = stripJsonExtension(file.getFileName().toString());
                readProfile(file, sourceKey).ifPresent(profiles::add);
            }
        }
        return List.copyOf(profiles);
    }

    private void ensureSeasonFile() throws IOException {
        Path seasonFile = dataRoot.resolve(SEASON_FILE);
        if (Files.exists(seasonFile)) return;

        Map<String, Object> season = new HashMap<>();
        season.put("dataVersion", configuration.dataVersion());
        season.put("season", 1);
        season.put("createdAt", clock.instant().toString());
        writeJsonAtomically(seasonFile, season);
    }

    private void normalize(StoredProgress progress) {
        int oldVersion = progress.dataVersion;
        progress.dataVersion = configuration.dataVersion();
        progress.name = progress.name == null ? "" : progress.name;
        progress.uuid = progress.uuid == null ? "" : progress.uuid;
        progress.wins = Math.max(0, progress.wins);
        progress.kills = Math.max(0, progress.kills);
        progress.deaths = Math.max(0, progress.deaths);
        progress.matchesPlayed = Math.max(0, progress.matchesPlayed);
        progress.coins = ProgressPolicy.normalizeCoins(progress.coins);
        progress.battlePassXp = Math.max(0, progress.battlePassXp);
        progress.classes = ProgressPolicy.normalizeNonNegativeValues(progress.classes);
        progress.classTiers = ProgressPolicy.normalizeNonNegativeValues(progress.classTiers);
        progress.unlockedBaseClasses = ProgressPolicy.normalizeNonNegativeValues(progress.unlockedBaseClasses);
        progress.purchasedClasses = ProgressPolicy.normalizeNonNegativeValues(progress.purchasedClasses);
        progress.donations = ProgressPolicy.normalizeNonNegativeValues(progress.donations);
        progress.stats = ProgressPolicy.normalizeNonNegativeValues(progress.stats);
        progress.appliedTransactionReceipts = PlayerTransactionReceiptLedger.normalizeReceipts(
                progress.appliedTransactionReceipts);

        for (String classId : configuration.initialBaseClasses()) {
            progress.unlockedBaseClasses.put(classId, 1);
        }
        if (oldVersion < 5) migrateLegacyBaseProgress(progress);

        for (String classId : configuration.allClasses()) {
            progress.classes.putIfAbsent(classId, 0);
            if (configuration.shopClasses().contains(classId)) progress.classes.put(classId, 0);
        }
        progress.classes.replaceAll((classId, xp) -> normalizeXp(xp == null ? 0 : xp));
        progress.classTiers.replaceAll((classId, tier) -> normalizeTier(tier == null ? 0 : tier));

        for (String classId : configuration.baseClasses()) {
            progress.classes.put(classId, normalizeXp(progress.classes.getOrDefault(classId, 0)));
            progress.classTiers.put(classId, normalizeTier(progress.classTiers.getOrDefault(classId, 0)));
            boolean unlocked = configuration.initialBaseClasses().contains(classId)
                    || progress.unlockedBaseClasses.getOrDefault(classId, 0) > 0;
            progress.unlockedBaseClasses.put(classId, unlocked ? 1 : 0);
        }
        for (String classId : configuration.shopClasses()) progress.purchasedClasses.putIfAbsent(classId, 0);
        for (String classId : configuration.exclusiveClasses()) progress.purchasedClasses.putIfAbsent(classId, 0);
    }

    private boolean requiresProgressionNormalization(StoredProgress progress) {
        if (progress.classes == null || progress.classTiers == null) return true;
        for (Integer xp : progress.classes.values()) {
            if (xp == null || xp != normalizeXp(xp)) return true;
        }
        for (Integer tier : progress.classTiers.values()) {
            if (tier == null || tier != normalizeTier(tier)) return true;
        }
        for (String classId : configuration.baseClasses()) {
            if (!progress.classes.containsKey(classId) || !progress.classTiers.containsKey(classId)) return true;
        }
        return false;
    }

    private void migrateLegacyBaseProgress(StoredProgress progress) {
        for (String classId : configuration.baseClasses()) {
            int xp = normalizeXp(progress.classes.getOrDefault(classId, 0));
            if (xp > 0 || configuration.initialBaseClasses().contains(classId)) {
                progress.unlockedBaseClasses.put(classId, 1);
            }
            progress.classTiers.putIfAbsent(classId, ClassTier.BASIC.id());
        }
    }

    private int normalizeXp(int xp) {
        return ProgressPolicy.normalizeExperience(xp, configuration.maximumExperience());
    }

    private int normalizeTier(int tier) {
        return ClassTier.clamp(tier).id();
    }

    private void updateIdentity(StoredProgress progress, UUID uuid, String lastKnownName) {
        String compact = compactUuid(uuid);
        String safeName = lastKnownName == null || lastKnownName.isBlank()
                ? progress.name == null || progress.name.isBlank() ? uuid.toString() : progress.name
                : lastKnownName;
        progress.uuid = compact;
        progress.name = safeName;
        if (progress.firstSeen <= 0L) progress.firstSeen = clock.millis();
        progress.lastSeen = clock.millis();
    }

    private ProgressSnapshot.Data toData(StoredProgress progress) {
        return new ProgressSnapshot.Data(
                progress.dataVersion,
                progress.name,
                progress.uuid,
                progress.classes,
                progress.classTiers,
                progress.unlockedBaseClasses,
                progress.wins,
                progress.kills,
                progress.deaths,
                progress.matchesPlayed,
                progress.coins,
                progress.battlePassXp,
                progress.xpBoost,
                progress.sadTromboneKills,
                progress.purchasedClasses,
                progress.donations,
                progress.stats,
                progress.appliedTransactionReceipts.stream()
                        .map(AppliedTransactionReceipt::toProgressReceipt)
                        .toList(),
                progress.firstSeen,
                progress.lastSeen
        );
    }

    private StoredProgress fromData(ProgressSnapshot.Data data) {
        StoredProgress stored = new StoredProgress();
        stored.dataVersion = data.dataVersion();
        stored.name = data.name();
        stored.uuid = data.uuid();
        stored.classes = new HashMap<>(data.classes());
        stored.classTiers = new HashMap<>(data.classTiers());
        stored.unlockedBaseClasses = new HashMap<>(data.unlockedBaseClasses());
        stored.wins = data.wins();
        stored.kills = data.kills();
        stored.deaths = data.deaths();
        stored.matchesPlayed = data.matchesPlayed();
        stored.coins = data.coins();
        stored.battlePassXp = data.battlePassXp();
        stored.xpBoost = data.xpBoost();
        stored.sadTromboneKills = data.sadTromboneKills();
        stored.purchasedClasses = new HashMap<>(data.purchasedClasses());
        stored.donations = new HashMap<>(data.donations());
        stored.stats = new HashMap<>(data.stats());
        stored.appliedTransactionReceipts = data.appliedTransactionReceipts().stream()
                .map(AppliedTransactionReceipt::from)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        stored.firstSeen = data.firstSeen();
        stored.lastSeen = data.lastSeen();
        return stored;
    }

    private UUID parseProgressUuid(String key, ProgressSnapshot.Data data) {
        UUID uuid = parseCompactUuid(data == null ? "" : data.uuid());
        return uuid != null ? uuid : parseCompactUuid(key);
    }

    static String storageKey(String name, UUID fallbackUuid) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9_.-]", "_");
        if (normalized.isBlank()) normalized = fallbackUuid == null ? "" : compactUuid(fallbackUuid);
        return normalized;
    }

    static String compactUuid(UUID uuid) {
        return uuid.toString().replace("-", "");
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

    private void writeJsonAtomically(Path file, Object value) throws IOException {
        FileSaveResult result = fileStore.write(file, writer -> GSON.toJson(value, writer));
        if (result.status() != FileSaveResult.Status.SUCCESS) {
            throw new IOException(result.diagnostic(), result.exception().orElse(null));
        }
    }

    private void backupCorruptFile(Path file) {
        if (file == null || !Files.exists(file)) return;
        Path target = backupsRoot.resolve(
                "corrupt_" + BACKUP_FORMAT.format(clock.instant()) + "_" + file.getFileName());
        try {
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            log.error("Failed to back up corrupt Tactical Tablet progress file " + file, exception);
        }
    }

    static void copyJsonFiles(Path sourceRoot, Path targetRoot) throws IOException {
        if (!Files.isDirectory(sourceRoot)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourceRoot, "*.json")) {
            for (Path source : stream) {
                Files.copy(source, targetRoot.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void cleanOldBackups() throws IOException {
        if (!Files.isDirectory(backupsRoot)) return;
        try (Stream<Path> stream = Files.list(backupsRoot)) {
            Path[] backups = stream
                    .filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toArray(Path[]::new);
            for (int index = MAX_BACKUP_FOLDERS; index < backups.length; index++) {
                deleteRecursively(backups[index]);
            }
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) {
                Files.deleteIfExists(path);
            }
        }
    }

    private void createBackup(BackupSnapshot snapshot) throws IOException {
        Path completed = backupsRoot.resolve(snapshot.timestamp());
        Path temporary = backupsRoot.resolve("." + snapshot.timestamp() + ".incomplete");
        Path temporaryPlayers = temporary.resolve(PLAYERS_DIRECTORY);
        Files.createDirectories(temporaryPlayers);
        copyJsonFiles(playersRoot, temporaryPlayers);

        List<String> files = new ArrayList<>();
        for (ProgressSnapshot player : snapshot.players()) {
            Path target = temporaryPlayers.resolve(player.key() + ".json");
            writeJsonAtomically(target, player.data());
            files.add(PLAYERS_DIRECTORY + "/" + target.getFileName());
        }

        Path season = dataRoot.resolve(SEASON_FILE);
        if (Files.isRegularFile(season)) {
            Files.copy(season, temporary.resolve(SEASON_FILE), StandardCopyOption.REPLACE_EXISTING);
            files.add(SEASON_FILE);
        }
        copyBackupFile(dataRoot.resolve("clans.json"), temporary.resolve("clans.json"), "clans.json", files);
        copyBackupTree(dataRoot.resolve("transactions"), temporary.resolve("transactions"), "transactions", files);
        copyBackupTree(dataRoot.resolve("migrations"), temporary.resolve("migrations"), "migrations", files);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(temporaryPlayers, "*.json")) {
            for (Path file : stream) {
                String entry = PLAYERS_DIRECTORY + "/" + file.getFileName();
                if (!files.contains(entry)) files.add(entry);
            }
        }

        Map<String, Object> manifest = new HashMap<>();
        manifest.put("schemaVersion", configuration.dataVersion());
        manifest.put("generationId", snapshot.revision());
        manifest.put("timestamp", snapshot.timestamp());
        manifest.put("files", List.copyOf(files));
        Map<String, Long> sizes = new HashMap<>();
        for (String file : files) sizes.put(file, Files.size(temporary.resolve(file)));
        manifest.put("sizes", Map.copyOf(sizes));
        writeJsonAtomically(temporary.resolve("manifest.json"), manifest);
        try {
            Files.move(temporary, completed, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, completed);
        }
        cleanOldBackups();
    }

    private static void copyBackupFile(Path source, Path target, String relative, List<String> files)
            throws IOException {
        if (!Files.isRegularFile(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(source) || excludedBackupFile(source)) return;
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        files.add(relative);
    }

    private static void copyBackupTree(Path sourceRoot, Path targetRoot, String prefix, List<String> files)
            throws IOException {
        if (!Files.isDirectory(sourceRoot, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(sourceRoot)) return;
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

    record Configuration(
            int dataVersion,
            int maximumExperience,
            Set<String> initialBaseClasses,
            Set<String> baseClasses,
            Set<String> shopClasses,
            Set<String> exclusiveClasses,
            Set<String> allClasses
    ) {
        Configuration {
            initialBaseClasses = normalizedSet(initialBaseClasses);
            baseClasses = normalizedSet(baseClasses);
            shopClasses = normalizedSet(shopClasses);
            exclusiveClasses = normalizedSet(exclusiveClasses);
            allClasses = normalizedSet(allClasses);
        }

        private static Set<String> normalizedSet(Set<String> values) {
            return values.stream()
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    record LoadedProfile(String sourceKey, ProgressSnapshot.Data data, boolean requiresSave) {
        LoadedProfile {
            Objects.requireNonNull(sourceKey, "sourceKey");
            Objects.requireNonNull(data, "data");
        }
    }

    interface RepositoryLog {
        void info(String message);

        void warn(String message);

        void error(String message, Throwable exception);

        static RepositoryLog noop() {
            return new RepositoryLog() {
                public void info(String message) { }
                public void warn(String message) { }
                public void error(String message, Throwable exception) { }
            };
        }
    }

    private final class PlayerWriteTask implements ModPersistenceExecutor.WriteTask {
        private final Path target;
        private final ProgressSnapshot snapshot;

        private PlayerWriteTask(Path target, ProgressSnapshot snapshot) {
            this.target = target;
            this.snapshot = snapshot;
        }

        public Path target() { return target; }

        public long revision() { return snapshot.revision(); }

        public FileSaveResult write() {
            return fileStore.write(target, writer -> GSON.toJson(snapshot.data(), writer));
        }
    }

    private record BackupSnapshot(String timestamp, List<ProgressSnapshot> players, long revision) {
        private BackupSnapshot {
            players = List.copyOf(players);
        }
    }

    private final class BackupWriteTask implements ModPersistenceExecutor.WriteTask {
        private final BackupSnapshot snapshot;

        private BackupWriteTask(BackupSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        public Path target() { return backupsRoot.resolve(snapshot.timestamp()); }

        public long revision() { return snapshot.revision(); }

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

    private static final class StoredProgress {
        private int dataVersion = 11;
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
    }
}
