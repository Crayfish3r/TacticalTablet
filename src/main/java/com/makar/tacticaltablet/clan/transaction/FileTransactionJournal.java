package com.makar.tacticaltablet.clan.transaction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.makar.tacticaltablet.storage.AtomicFileStore;
import com.makar.tacticaltablet.storage.FileSaveResult;
import com.makar.tacticaltablet.storage.TacticalTabletStoragePaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** JSON WAL implementation for create-clan transactions. */
public final class FileTransactionJournal implements TransactionJournal {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final TacticalTabletStoragePaths paths;
    private final AtomicFileStore fileStore;
    private final FileOperations fileOperations;

    public FileTransactionJournal(TacticalTabletStoragePaths paths, AtomicFileStore fileStore) {
        this(paths, fileStore, new DefaultFileOperations());
    }

    FileTransactionJournal(TacticalTabletStoragePaths paths, AtomicFileStore fileStore, FileOperations fileOperations) {
        this.paths = Objects.requireNonNull(paths, "paths");
        this.fileStore = Objects.requireNonNull(fileStore, "fileStore");
        this.fileOperations = Objects.requireNonNull(fileOperations, "fileOperations");
    }

    @Override
    public JournalResult prepare(CreateClanTransaction transaction) {
        try {
            validate(transaction);
        } catch (InvalidJournalException exception) {
            return JournalResult.failure("Invalid transaction journal: " + exception.getMessage(), exception);
        }
        if (transaction.state() != TransactionState.PREPARED) {
            return JournalResult.failure("Initial journal state must be PREPARED", null);
        }
        Path file = transactionPath(transaction);
        if (Files.exists(file)) {
            return JournalResult.failure("Transaction journal already exists: " + transaction.transactionId(), null);
        }
        return write(file, transaction);
    }

    @Override
    public JournalResult advance(CreateClanTransaction transaction, TransactionState targetState, String diagnostic) {
        Path file = transactionPath(transaction);
        CreateClanTransaction persisted;
        try {
            validate(transaction);
            persisted = readValidated(file);
        } catch (IOException | InvalidJournalException exception) {
            return JournalResult.failure("Cannot read transaction journal " + transaction.transactionId(), exception);
        }
        try {
            validateIdentity(persisted, transaction);
        } catch (InvalidJournalException exception) {
            return JournalResult.failure("Transaction identity mismatch for " + transaction.transactionId(), exception);
        }
        if (persisted.state() == targetState) {
            return JournalResult.success();
        }
        if (!isAllowedTransition(persisted.state(), targetState)) {
            return JournalResult.failure("Invalid journal transition " + persisted.state() + " -> " + targetState, null);
        }
        return write(file, persisted.withState(targetState, Instant.now().toEpochMilli(), diagnostic));
    }

    @Override
    public JournalLoadResult loadPending() {
        Path root = paths.transactionsDirectory();
        List<CreateClanTransaction> transactions = new ArrayList<>();
        List<CreateClanTransaction> rollbackRequired = new ArrayList<>();
        List<CreateClanTransaction> committed = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        int quarantined = 0;
        int quarantineFailures = 0;
        int backupFailures = 0;
        int reasonWriteFailures = 0;
        int archived = 0;
        int archiveFailures = 0;
        try {
            Files.createDirectories(root);
            try (DirectoryStream<Path> files = Files.newDirectoryStream(root, "*.json")) {
                for (Path file : files) {
                    try {
                        CreateClanTransaction transaction = readValidated(file);
                        if (transaction.state().isAutoRecoverable()) {
                            transactions.add(transaction);
                        } else if (transaction.state().requiresManualRecovery()) {
                            rollbackRequired.add(transaction);
                            diagnostics.add("transactionId=" + transaction.transactionId()
                                    + " state=" + transaction.state()
                                    + " type=ROLLBACK_REQUIRED message=manual recovery required"
                                    + " playerUuid=" + transaction.playerUuid()
                                    + " clanId=" + transaction.clanId());
                        } else if (transaction.state().isCommitted()) {
                            committed.add(transaction);
                            JournalResult archivedResult = archiveCommitted(transaction);
                            if (archivedResult.status() == JournalResult.Status.SUCCESS) {
                                archived++;
                            } else {
                                archiveFailures++;
                                diagnostics.add("Could not archive committed clan transaction "
                                        + transaction.transactionId() + ": " + archivedResult.diagnostic());
                            }
                        }
                    } catch (IOException | InvalidJournalException exception) {
                        IsolationResult isolation = isolateCorrupt(file, exception);
                        if (isolation.status() == IsolationStatus.ISOLATED
                                || isolation.status() == IsolationStatus.REASON_WRITE_FAILED) {
                            quarantined++;
                        }
                        if (isolation.status() == IsolationStatus.REASON_WRITE_FAILED) {
                            reasonWriteFailures++;
                        } else if (isolation.status() == IsolationStatus.BACKUP_FAILED) {
                            backupFailures++;
                        } else if (isolation.status() == IsolationStatus.MOVE_FAILED) {
                            quarantineFailures++;
                        }
                        diagnostics.add(isolation.diagnostic());
                    }
                }
            }
        } catch (IOException exception) {
            diagnostics.add("Cannot list clan transaction journals: " + exception.getMessage());
        }
        transactions.sort(Comparator.comparing(transaction -> transaction.transactionId().toString()));
        rollbackRequired.sort(Comparator.comparing(transaction -> transaction.transactionId().toString()));
        committed.sort(Comparator.comparing(transaction -> transaction.transactionId().toString()));
        return new JournalLoadResult(
                transactions,
                rollbackRequired,
                committed,
                quarantined,
                quarantineFailures,
                backupFailures,
                reasonWriteFailures,
                archived,
                archiveFailures,
                diagnostics
        );
    }

    @Override
    public JournalResult archiveCommitted(CreateClanTransaction transaction) {
        try {
            validate(transaction);
            if (!transaction.state().isCommitted()) {
                return JournalResult.failure("Only COMMITTED journals can be archived", null);
            }
            Path source = transactionPath(transaction);
            Path archive = uniquePath(paths.transactionsDirectory().resolve("archive").resolve(source.getFileName()));
            Path canonicalArchive = paths.transactionsDirectory().resolve("archive").resolve(source.getFileName());
            if (!fileOperations.exists(source)) {
                return fileOperations.exists(canonicalArchive)
                        ? JournalResult.success()
                        : JournalResult.failure("Committed journal is missing: " + transaction.transactionId(), null);
            }
            fileOperations.createDirectories(archive.getParent());
            fileOperations.move(source, archive);
            return JournalResult.success();
        } catch (IOException | InvalidJournalException exception) {
            return JournalResult.failure("Archive committed journal failed: " + exception.getMessage(), exception);
        }
    }

    private JournalResult write(Path file, CreateClanTransaction transaction) {
        FileSaveResult result = fileStore.write(file, writer -> GSON.toJson(transaction, writer));
        if (result.status() == FileSaveResult.Status.SUCCESS) {
            return JournalResult.success();
        }
        return JournalResult.failure(result.diagnostic(), result.exception().orElse(null));
    }

    CreateClanTransaction readValidated(Path file) throws IOException, InvalidJournalException {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        if (json.isBlank()) {
            throw new InvalidJournalException("Journal is empty");
        }
        JsonElement element;
        try {
            element = JsonParser.parseString(json);
        } catch (JsonParseException exception) {
            throw new InvalidJournalException("Journal JSON is malformed", exception);
        }
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            throw new InvalidJournalException("Journal root must be an object");
        }
        validateJsonObject(element.getAsJsonObject());
        try {
            CreateClanTransaction transaction = GSON.fromJson(element, CreateClanTransaction.class);
            validate(transaction);
            return transaction;
        } catch (JsonParseException | IllegalArgumentException | NullPointerException exception) {
            throw new InvalidJournalException("Journal object is invalid: " + exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            throw new InvalidJournalException("Journal object is invalid: " + exception.getMessage(), exception);
        }
    }

    private IsolationResult isolateCorrupt(Path source, Exception exception) {
        Path backup = null;
        Path quarantine = null;
        Path reason = null;
        try {
            if (!fileOperations.exists(source)) {
                return isolationResult(IsolationStatus.BACKUP_FAILED, source, null, null, null,
                        "source file no longer exists", exception);
            }
            String evidenceName = "corrupt_" + Instant.now().toEpochMilli() + "_" + source.getFileName();
            Path backupDirectory = paths.backupsDirectory().resolve("transactions");
            Path quarantineDirectory = paths.transactionsDirectory().resolve("quarantine");
            fileOperations.createDirectories(backupDirectory);
            fileOperations.createDirectories(quarantineDirectory);
            backup = uniquePath(backupDirectory.resolve(evidenceName));
            quarantine = uniquePath(quarantineDirectory.resolve(evidenceName));
            reason = uniquePath(quarantine.resolveSibling(quarantine.getFileName() + ".reason.txt"));
            fileOperations.copy(source, backup);
            try {
                fileOperations.move(source, quarantine);
            } catch (IOException moveFailure) {
                exception.addSuppressed(moveFailure);
                return isolationResult(IsolationStatus.MOVE_FAILED, source, backup, quarantine, reason,
                        moveFailure.getMessage(), exception);
            }
            try {
                fileOperations.writeString(reason,
                    "source=" + source.getFileName() + System.lineSeparator()
                            + "category=" + exception.getClass().getSimpleName() + System.lineSeparator()
                            + "message=" + safeDiagnostic(exception.getMessage()) + System.lineSeparator()
                            + "backup=" + backup.getFileName() + System.lineSeparator()
                            + "quarantine=" + quarantine.getFileName() + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            } catch (IOException reasonFailure) {
                exception.addSuppressed(reasonFailure);
                return isolationResult(IsolationStatus.REASON_WRITE_FAILED, source, backup, quarantine, reason,
                        reasonFailure.getMessage(), exception);
            }
            return isolationResult(IsolationStatus.ISOLATED, source, backup, quarantine, reason, "", exception);
        } catch (IOException isolationFailure) {
            exception.addSuppressed(isolationFailure);
            return isolationResult(IsolationStatus.BACKUP_FAILED, source, backup, quarantine, reason,
                    isolationFailure.getMessage(), exception);
        }
    }

    private Path transactionPath(CreateClanTransaction transaction) {
        return paths.transactionsDirectory().resolve(transaction.transactionId() + ".json");
    }

    private static boolean isAllowedTransition(TransactionState from, TransactionState to) {
        if (to == TransactionState.ROLLBACK_REQUIRED) return from.isAutoRecoverable();
        return (from == TransactionState.PREPARED && to == TransactionState.PLAYER_APPLIED)
                || (from == TransactionState.PLAYER_APPLIED && to == TransactionState.CLAN_APPLIED)
                || (from == TransactionState.CLAN_APPLIED && to == TransactionState.COMMITTED);
    }

    private static void validate(CreateClanTransaction transaction) throws InvalidJournalException {
        if (transaction == null) {
            throw new InvalidJournalException("Journal transaction is missing");
        }
        if (transaction.schemaVersion() != CreateClanTransaction.SCHEMA_VERSION) {
            throw new InvalidJournalException("Unsupported journal schema " + transaction.schemaVersion());
        }
        if (!CreateClanTransaction.OPERATION_TYPE.equals(transaction.operationType())) {
            throw new InvalidJournalException("Unsupported operation " + transaction.operationType());
        }
        if (transaction.transactionId() == null || transaction.playerUuid() == null || transaction.state() == null) {
            throw new InvalidJournalException("Journal identity fields are required");
        }
        if (transaction.playerName() == null || transaction.playerName().isBlank() || transaction.playerName().length() > 64) {
            throw new InvalidJournalException("Journal player name is invalid");
        }
        if (transaction.clanId() == null || transaction.clanId().isBlank() || transaction.clanId().length() > 128) {
            throw new InvalidJournalException("Journal clan ID is invalid");
        }
        if (transaction.clanPayload() == null) {
            throw new InvalidJournalException("Journal clan payload is required");
        }
        ClanCreationPayload payload = transaction.clanPayload();
        if (payload.clanId() == null || payload.clanId().isBlank()
                || payload.name() == null || payload.name().isBlank() || payload.name().length() > 64
                || payload.tag() == null || payload.tag().isBlank() || payload.tag().length() > 16
                || payload.ownerUuid() == null
                || payload.ownerName() == null || payload.ownerName().isBlank() || payload.ownerName().length() > 64) {
            throw new InvalidJournalException("Journal clan payload fields are invalid");
        }
        if (!transaction.clanId().equals(transaction.clanPayload().clanId())) {
            throw new InvalidJournalException("Journal clan ID does not match payload");
        }
        if (!transaction.playerUuid().equals(payload.ownerUuid())) {
            throw new InvalidJournalException("Journal payer UUID does not match clan owner UUID");
        }
        if (!CreateClanTransaction.normalizeDisplayName(transaction.playerName())
                .equals(CreateClanTransaction.normalizeDisplayName(payload.ownerName()))) {
            throw new InvalidJournalException("Journal payer name does not match clan owner name");
        }
        if (!transaction.payloadHash().equals(CreateClanTransaction.payloadHash(transaction.clanPayload()))) {
            throw new InvalidJournalException("Journal payload hash does not match payload");
        }
        if (transaction.expectedOldBalance() < 0 || transaction.newBalance() < 0
                || transaction.expectedOldBalance() <= transaction.newBalance()) {
            throw new InvalidJournalException("Invalid journal balance transition");
        }
        if (transaction.createdAt() <= 0 || transaction.updatedAt() <= 0 || transaction.updatedAt() < transaction.createdAt()) {
            throw new InvalidJournalException("Invalid journal timestamps");
        }
        if (transaction.diagnostic() != null && transaction.diagnostic().length() > 512) {
            throw new InvalidJournalException("Journal diagnostic is too long");
        }
    }

    private static void validateIdentity(CreateClanTransaction persisted, CreateClanTransaction requested)
            throws InvalidJournalException {
        if (!Objects.equals(persisted.transactionId(), requested.transactionId())
                || !Objects.equals(persisted.operationType(), requested.operationType())
                || !Objects.equals(persisted.playerUuid(), requested.playerUuid())
                || !Objects.equals(persisted.clanId(), requested.clanId())
                || !Objects.equals(persisted.payloadHash(), requested.payloadHash())
                || persisted.expectedOldBalance() != requested.expectedOldBalance()
                || persisted.newBalance() != requested.newBalance()) {
            throw new InvalidJournalException("Persisted journal identity differs from requested transition");
        }
    }

    private static void validateJsonObject(JsonObject object) throws InvalidJournalException {
        requireInt(object, "schemaVersion");
        requireUuid(object, "transactionId");
        requireString(object, "operationType", 64);
        requireUuid(object, "playerUuid");
        requireString(object, "playerName", 64);
        requireString(object, "clanId", 128);
        requireInt(object, "expectedOldBalance");
        requireInt(object, "newBalance");
        requireString(object, "payloadHash", 128);
        requireString(object, "state", 32);
        requireLong(object, "createdAt");
        requireLong(object, "updatedAt");
        if (!object.has("clanPayload") || !object.get("clanPayload").isJsonObject()) {
            throw new InvalidJournalException("Missing object field clanPayload");
        }
        JsonObject payload = object.getAsJsonObject("clanPayload");
        requireString(payload, "clanId", 128);
        requireString(payload, "name", 64);
        requireString(payload, "tag", 16);
        requireInt(payload, "color");
        requireUuid(payload, "ownerUuid");
        requireString(payload, "ownerName", 64);
        try {
            TransactionState.valueOf(object.get("state").getAsString());
        } catch (IllegalArgumentException exception) {
            throw new InvalidJournalException("Unknown journal state", exception);
        }
    }

    private static String requireString(JsonObject object, String name, int maxLength) throws InvalidJournalException {
        if (!object.has(name) || object.get(name).isJsonNull() || !object.get(name).isJsonPrimitive()) {
            throw new InvalidJournalException("Missing string field " + name);
        }
        String value = object.get(name).getAsString();
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new InvalidJournalException("Invalid string field " + name);
        }
        return value;
    }

    private static void requireUuid(JsonObject object, String name) throws InvalidJournalException {
        try {
            UUID.fromString(requireString(object, name, 64));
        } catch (IllegalArgumentException exception) {
            throw new InvalidJournalException("Invalid UUID field " + name, exception);
        }
    }

    private static void requireInt(JsonObject object, String name) throws InvalidJournalException {
        try {
            if (!object.has(name) || object.get(name).isJsonNull()) throw new NumberFormatException(name);
            object.get(name).getAsInt();
        } catch (ClassCastException | IllegalStateException | NumberFormatException exception) {
            throw new InvalidJournalException("Invalid int field " + name, exception);
        }
    }

    private static void requireLong(JsonObject object, String name) throws InvalidJournalException {
        try {
            if (!object.has(name) || object.get(name).isJsonNull()) throw new NumberFormatException(name);
            object.get(name).getAsLong();
        } catch (ClassCastException | IllegalStateException | NumberFormatException exception) {
            throw new InvalidJournalException("Invalid long field " + name, exception);
        }
    }

    private Path uniquePath(Path preferred) throws IOException {
        if (!fileOperations.exists(preferred)) return preferred;
        String fileName = preferred.getFileName().toString();
        Path parent = preferred.getParent();
        for (int index = 1; index < 10_000; index++) {
            Path candidate = parent.resolve(fileName + "." + index);
            if (!fileOperations.exists(candidate)) return candidate;
        }
        throw new IOException("Cannot allocate unique journal evidence path for " + preferred.getFileName());
    }

    private static String safeDiagnostic(String message) {
        if (message == null || message.isBlank()) return "";
        return message.length() <= 256 ? message : message.substring(0, 256);
    }

    private static IsolationResult isolationResult(
            IsolationStatus status,
            Path source,
            Path backup,
            Path quarantine,
            Path reason,
            String reasonMessage,
            Exception exception
    ) {
        String diagnostic = "source=" + fileName(source)
                + " isolationStatus=" + status
                + " backup=" + fileName(backup)
                + " quarantine=" + fileName(quarantine)
                + " reason=" + fileName(reason)
                + " message=" + safeDiagnostic(reasonMessage == null || reasonMessage.isBlank()
                ? exception.getMessage()
                : reasonMessage);
        return new IsolationResult(status, source, backup, quarantine, reason, diagnostic, exception);
    }

    private static String fileName(Path path) {
        return path == null || path.getFileName() == null ? "" : path.getFileName().toString();
    }

    enum IsolationStatus {
        ISOLATED,
        BACKUP_FAILED,
        MOVE_FAILED,
        REASON_WRITE_FAILED
    }

    record IsolationResult(
            IsolationStatus status,
            Path source,
            Path backup,
            Path quarantine,
            Path reason,
            String diagnostic,
            Exception exception
    ) {
    }

    interface FileOperations {
        boolean exists(Path path) throws IOException;

        void createDirectories(Path path) throws IOException;

        void copy(Path source, Path target) throws IOException;

        void move(Path source, Path target) throws IOException;

        void writeString(Path target, String value, java.nio.charset.Charset charset) throws IOException;
    }

    private static final class DefaultFileOperations implements FileOperations {
        @Override
        public boolean exists(Path path) {
            return Files.exists(path);
        }

        @Override
        public void createDirectories(Path path) throws IOException {
            Files.createDirectories(path);
        }

        @Override
        public void copy(Path source, Path target) throws IOException {
            Files.copy(source, target);
        }

        @Override
        public void move(Path source, Path target) throws IOException {
            Files.move(source, target);
        }

        @Override
        public void writeString(Path target, String value, java.nio.charset.Charset charset) throws IOException {
            Files.writeString(target, value, charset);
        }
    }
}
