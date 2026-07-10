package com.makar.tacticaltablet.clan.transaction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.makar.tacticaltablet.storage.AtomicFileStore;
import com.makar.tacticaltablet.storage.FileSaveResult;
import com.makar.tacticaltablet.storage.TacticalTabletStoragePaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** JSON WAL implementation for create-clan transactions. */
public final class FileTransactionJournal implements TransactionJournal {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final TacticalTabletStoragePaths paths;
    private final AtomicFileStore fileStore;

    public FileTransactionJournal(TacticalTabletStoragePaths paths, AtomicFileStore fileStore) {
        this.paths = Objects.requireNonNull(paths, "paths");
        this.fileStore = Objects.requireNonNull(fileStore, "fileStore");
    }

    @Override
    public JournalResult prepare(CreateClanTransaction transaction) {
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
            persisted = read(file);
        } catch (IOException | JsonParseException | IllegalStateException exception) {
            return JournalResult.failure("Cannot read transaction journal " + transaction.transactionId(), exception);
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
        List<String> diagnostics = new ArrayList<>();
        try {
            Files.createDirectories(root);
            try (DirectoryStream<Path> files = Files.newDirectoryStream(root, "*.json")) {
                for (Path file : files) {
                    try {
                        CreateClanTransaction transaction = read(file);
                        validate(transaction);
                        if (!transaction.state().isTerminal()) {
                            transactions.add(transaction);
                        }
                    } catch (IOException | JsonParseException | IllegalArgumentException exception) {
                        String diagnostic = "Corrupt clan transaction journal isolated: " + file.getFileName();
                        diagnostics.add(diagnostic);
                        isolateCorrupt(file, exception, diagnostics);
                    }
                }
            }
        } catch (IOException exception) {
            diagnostics.add("Cannot list clan transaction journals: " + exception.getMessage());
        }
        transactions.sort(Comparator.comparing(transaction -> transaction.transactionId().toString()));
        return new JournalLoadResult(transactions, diagnostics);
    }

    private JournalResult write(Path file, CreateClanTransaction transaction) {
        FileSaveResult result = fileStore.write(file, writer -> GSON.toJson(transaction, writer));
        if (result.status() == FileSaveResult.Status.SUCCESS) {
            return JournalResult.success();
        }
        return JournalResult.failure(result.diagnostic(), result.exception().orElse(null));
    }

    private CreateClanTransaction read(Path file) throws IOException {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            CreateClanTransaction transaction = GSON.fromJson(reader, CreateClanTransaction.class);
            if (transaction == null) throw new IllegalStateException("Journal is empty");
            return transaction;
        }
    }

    private void isolateCorrupt(Path source, Exception exception, List<String> diagnostics) {
        Path backup = paths.backupsDirectory().resolve("transactions")
                .resolve("corrupt_" + Instant.now().toEpochMilli() + "_" + source.getFileName());
        Path quarantine = paths.transactionsDirectory().resolve("quarantine").resolve(source.getFileName());
        try {
            Files.createDirectories(backup.getParent());
            Files.createDirectories(quarantine.getParent());
            Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);
            Files.move(source, quarantine, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException isolationFailure) {
            exception.addSuppressed(isolationFailure);
            diagnostics.add("Could not isolate corrupt journal " + source.getFileName() + ": " + isolationFailure.getMessage());
        }
    }

    private Path transactionPath(CreateClanTransaction transaction) {
        return paths.transactionsDirectory().resolve(transaction.transactionId() + ".json");
    }

    private static boolean isAllowedTransition(TransactionState from, TransactionState to) {
        if (to == TransactionState.ROLLBACK_REQUIRED) return !from.isTerminal();
        return (from == TransactionState.PREPARED && to == TransactionState.PLAYER_APPLIED)
                || (from == TransactionState.PLAYER_APPLIED && to == TransactionState.CLAN_APPLIED)
                || (from == TransactionState.CLAN_APPLIED && to == TransactionState.COMMITTED);
    }

    private static void validate(CreateClanTransaction transaction) {
        if (transaction.schemaVersion() != CreateClanTransaction.SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported journal schema " + transaction.schemaVersion());
        }
        if (!CreateClanTransaction.OPERATION_TYPE.equals(transaction.operationType())) {
            throw new IllegalStateException("Unsupported operation " + transaction.operationType());
        }
        if (!transaction.clanId().equals(transaction.clanPayload().clanId())) {
            throw new IllegalStateException("Journal clan ID does not match payload");
        }
        if (!transaction.payloadHash().equals(CreateClanTransaction.payloadHash(transaction.clanPayload()))) {
            throw new IllegalStateException("Journal payload hash does not match payload");
        }
        if (transaction.expectedOldBalance() < transaction.newBalance() || transaction.newBalance() < 0) {
            throw new IllegalStateException("Invalid journal balance transition");
        }
    }
}
