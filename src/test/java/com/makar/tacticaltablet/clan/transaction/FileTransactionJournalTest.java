package com.makar.tacticaltablet.clan.transaction;

import com.makar.tacticaltablet.storage.AtomicFileStore;
import com.makar.tacticaltablet.storage.TacticalTabletStoragePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileTransactionJournalTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void corruptEntryIsBackedUpAndDoesNotBlockOtherRecoveryEntries() throws IOException {
        TacticalTabletStoragePaths paths = new TacticalTabletStoragePaths(temporaryDirectory);
        FileTransactionJournal journal = new FileTransactionJournal(paths, new AtomicFileStore());
        CreateClanTransaction transaction = transaction();

        assertEquals(TransactionJournal.JournalResult.Status.SUCCESS, journal.prepare(transaction).status());
        Path corrupt = paths.transactionsDirectory().resolve("corrupt.json");
        Files.createDirectories(corrupt.getParent());
        Files.writeString(corrupt, "{not valid json");

        TransactionJournal.JournalLoadResult result = journal.loadPending();

        assertEquals(1, result.transactions().size());
        assertEquals(transaction.transactionId(), result.transactions().get(0).transactionId());
        assertFalse(result.diagnostics().isEmpty());
        assertFalse(Files.exists(corrupt));
        try (var quarantined = Files.list(paths.transactionsDirectory().resolve("quarantine"))) {
            assertTrue(quarantined.anyMatch(path -> path.getFileName().toString().contains("corrupt.json")));
        }
        try (var backups = Files.list(paths.backupsDirectory().resolve("transactions"))) {
            assertTrue(backups.anyMatch(path -> path.getFileName().toString().endsWith("_corrupt.json")));
        }
    }

    @Test
    void committedEntryIsArchivedAndNotReturnedForRecovery() throws IOException {
        TacticalTabletStoragePaths paths = new TacticalTabletStoragePaths(temporaryDirectory);
        FileTransactionJournal journal = new FileTransactionJournal(paths, new AtomicFileStore());
        CreateClanTransaction committed = transaction().withState(TransactionState.COMMITTED, 2L, "done");

        Files.createDirectories(paths.transactionsDirectory());
        Files.writeString(paths.transactionsDirectory().resolve(committed.transactionId() + ".json"),
                new com.google.gson.GsonBuilder().create().toJson(committed));

        TransactionJournal.JournalLoadResult result = journal.loadPending();

        assertEquals(0, result.transactions().size());
        assertEquals(1, result.committed().size());
        assertEquals(1, result.archived());
        assertTrue(Files.exists(paths.transactionsDirectory().resolve("archive").resolve(committed.transactionId() + ".json")));
    }

    @Test
    void rollbackRequiredEntryIsReportedButNotReturnedForAutomaticRecovery() throws IOException {
        TacticalTabletStoragePaths paths = new TacticalTabletStoragePaths(temporaryDirectory);
        FileTransactionJournal journal = new FileTransactionJournal(paths, new AtomicFileStore());
        CreateClanTransaction rollback = transaction().withState(TransactionState.ROLLBACK_REQUIRED, 2L, "manual");

        Files.createDirectories(paths.transactionsDirectory());
        Files.writeString(paths.transactionsDirectory().resolve(rollback.transactionId() + ".json"),
                new com.google.gson.GsonBuilder().create().toJson(rollback));

        TransactionJournal.JournalLoadResult result = journal.loadPending();

        assertEquals(0, result.transactions().size());
        assertEquals(List.of(rollback.transactionId()), result.rollbackRequired().stream().map(CreateClanTransaction::transactionId).toList());
        assertFalse(result.diagnostics().isEmpty());
        assertTrue(Files.exists(paths.transactionsDirectory().resolve(rollback.transactionId() + ".json")));
    }

    @Test
    void invalidJournalVariantsAreQuarantinedIndependently() throws IOException {
        TacticalTabletStoragePaths paths = new TacticalTabletStoragePaths(temporaryDirectory);
        FileTransactionJournal journal = new FileTransactionJournal(paths, new AtomicFileStore());
        Files.createDirectories(paths.transactionsDirectory());
        Files.writeString(paths.transactionsDirectory().resolve("empty.json"), "");
        Files.writeString(paths.transactionsDirectory().resolve("null.json"), "null");
        Files.writeString(paths.transactionsDirectory().resolve("missing-id.json"), "{}");
        journal.prepare(transaction());

        TransactionJournal.JournalLoadResult result = journal.loadPending();

        assertEquals(1, result.transactions().size());
        assertEquals(3, result.quarantined());
        try (var quarantined = Files.list(paths.transactionsDirectory().resolve("quarantine"))) {
            assertEquals(3, quarantined.filter(path -> path.getFileName().toString().endsWith(".json")).count());
        }
    }

    @Test
    void advanceRejectsImmutableIdentityMismatch() {
        TacticalTabletStoragePaths paths = new TacticalTabletStoragePaths(temporaryDirectory);
        FileTransactionJournal journal = new FileTransactionJournal(paths, new AtomicFileStore());
        CreateClanTransaction transaction = transaction();
        CreateClanTransaction mismatch = new CreateClanTransaction(
                transaction.schemaVersion(),
                transaction.transactionId(),
                transaction.operationType(),
                transaction.playerUuid(),
                transaction.playerName(),
                transaction.clanId(),
                transaction.expectedOldBalance() + 1,
                transaction.newBalance(),
                transaction.clanPayload(),
                transaction.payloadHash(),
                transaction.state(),
                transaction.createdAt(),
                transaction.updatedAt(),
                ""
        );

        assertEquals(TransactionJournal.JournalResult.Status.SUCCESS, journal.prepare(transaction).status());
        assertEquals(TransactionJournal.JournalResult.Status.FAILED,
                journal.advance(mismatch, TransactionState.PLAYER_APPLIED, "bad").status());
    }

    private static CreateClanTransaction transaction() {
        UUID player = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID transaction = UUID.fromString("22222222-2222-2222-2222-222222222222");
        String clanId = "33333333-3333-3333-3333-333333333333";
        ClanCreationPayload payload = new ClanCreationPayload(clanId, "Example Clan", "TAG", 0x11AA11, player, "Player");
        return new CreateClanTransaction(1, transaction, CreateClanTransaction.OPERATION_TYPE, player, "Player", clanId,
                250, 150, payload, CreateClanTransaction.payloadHash(payload), TransactionState.PREPARED, 1L, 1L, "");
    }
}
