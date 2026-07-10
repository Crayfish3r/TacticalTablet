package com.makar.tacticaltablet.clan.transaction;

import com.makar.tacticaltablet.storage.AtomicFileStore;
import com.makar.tacticaltablet.storage.TacticalTabletStoragePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        assertTrue(Files.exists(paths.transactionsDirectory().resolve("quarantine/corrupt.json")));
        try (var backups = Files.list(paths.backupsDirectory().resolve("transactions"))) {
            assertTrue(backups.anyMatch(path -> path.getFileName().toString().endsWith("_corrupt.json")));
        }
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
