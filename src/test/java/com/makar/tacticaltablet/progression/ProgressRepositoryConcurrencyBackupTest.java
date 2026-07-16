package com.makar.tacticaltablet.progression;

import com.makar.tacticaltablet.storage.DurableSaveResult;
import com.makar.tacticaltablet.storage.ModPersistenceExecutor;
import com.makar.tacticaltablet.storage.SaveTicket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressRepositoryConcurrencyBackupTest {
    @TempDir
    Path temporaryRoot;

    @Test
    void newerRevisionWinsAndStaleWriteCannotOverwriteIt() throws Exception {
        try (ProgressRepository repository = ProgressRepositoryTestSupport.repository(temporaryRoot)) {
            SaveTicket first = repository.save(ProgressRepositoryTestSupport.snapshot("player", 1, 10), false);
            SaveTicket second = repository.save(ProgressRepositoryTestSupport.snapshot("player", 2, 20), false);
            SaveTicket stale = repository.save(ProgressRepositoryTestSupport.snapshot("player", 1, 99), false);

            DurableSaveResult firstResult = await(first);
            assertTrue(firstResult.status() == DurableSaveResult.Status.WRITTEN
                    || firstResult.status() == DurableSaveResult.Status.SUPERSEDED);
            assertEquals(DurableSaveResult.Status.WRITTEN, await(second).status());
            assertEquals(DurableSaveResult.Status.STALE_REJECTED, await(stale).status());
            assertEquals(20, repository.loadByKey("player").orElseThrow().data().coins());
            assertEquals(2, repository.completedRevision("player"));
        }
    }

    @Test
    void sequentialAndConcurrentDifferentPlayerSavesFlushIndependently() throws Exception {
        try (ProgressRepository repository = ProgressRepositoryTestSupport.repository(temporaryRoot)) {
            List<CompletableFuture<DurableSaveResult>> writes = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                String key = "player-" + index;
                writes.add(repository.save(ProgressRepositoryTestSupport.snapshot(key, 1, index), false)
                        .completion().toCompletableFuture());
            }

            assertTrue(repository.flush(Duration.ofSeconds(10)));
            for (CompletableFuture<DurableSaveResult> write : writes) {
                assertEquals(DurableSaveResult.Status.WRITTEN, write.get(1, TimeUnit.SECONDS).status());
            }
            for (int index = 0; index < 12; index++) {
                assertEquals(index, repository.loadByKey("player-" + index).orElseThrow().data().coins());
            }
        }
    }

    @Test
    void shutdownFlushWritesAcceptedSnapshotsAndRejectsNormalIntakeAfterStop() throws Exception {
        ProgressRepository repository = ProgressRepositoryTestSupport.repository(temporaryRoot);
        SaveTicket accepted = repository.save(ProgressRepositoryTestSupport.snapshot("accepted", 1, 10), false);
        repository.stopAccepting();
        SaveTicket rejected = repository.save(ProgressRepositoryTestSupport.snapshot("rejected", 1, 20), false);

        assertTrue(repository.flush(Duration.ofSeconds(10)));
        assertEquals(DurableSaveResult.Status.WRITTEN, await(accepted).status());
        assertEquals(DurableSaveResult.Status.EXECUTOR_STOPPED, await(rejected).status());
        repository.close();
        assertTrue(Files.exists(repository.playerFile("accepted")));
        assertFalse(Files.exists(repository.playerFile("rejected")));
    }

    @Test
    void failureForOneTargetDoesNotBlockSubsequentPlayer() throws Exception {
        ProgressRepository.RepositoryLog log = ProgressRepository.RepositoryLog.noop();
        com.makar.tacticaltablet.storage.AtomicFileStore selectiveStore =
                new com.makar.tacticaltablet.storage.AtomicFileStore((source, target, options) -> {
                    if (target.getFileName().toString().equals("broken.json")) {
                        throw new java.io.IOException("injected failure");
                    }
                    Files.move(source, target, options);
                });
        try (ProgressRepository repository = new ProgressRepository(
                temporaryRoot,
                ProgressRepositoryTestSupport.CONFIGURATION,
                ProgressRepositoryTestSupport.CLOCK,
                selectiveStore,
                log,
                64
        )) {
            repository.initialize();
            SaveTicket failed = repository.save(ProgressRepositoryTestSupport.snapshot("broken", 1, 1), false);
            SaveTicket healthy = repository.save(ProgressRepositoryTestSupport.snapshot("healthy", 1, 2), false);

            assertEquals(DurableSaveResult.Status.FAILED, await(failed).status());
            assertEquals(DurableSaveResult.Status.WRITTEN, await(healthy).status());
            assertEquals(2, repository.loadByKey("healthy").orElseThrow().data().coins());
        }
    }

    @Test
    void backupUsesSnapshotNamingAndDoesNotChangeActiveProfile() throws Exception {
        try (ProgressRepository repository = ProgressRepositoryTestSupport.repository(temporaryRoot)) {
            ProgressSnapshot active = ProgressRepositoryTestSupport.snapshot("player", 1, 10);
            await(repository.save(active, false));
            ModPersistenceExecutor.SubmitResult submitted = repository.backup(List.of(active), 1);

            assertEquals(ModPersistenceExecutor.SubmitStatus.ACCEPTED, submitted.status());
            assertTrue(repository.flush(Duration.ofSeconds(10)));
            try (Stream<Path> backups = Files.list(repository.backupsRoot())) {
                Path backup = backups.filter(Files::isDirectory).findFirst().orElseThrow();
                assertTrue(backup.getFileName().toString()
                        .matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}_1"));
                assertTrue(Files.exists(backup.resolve("players/player.json")));
                assertTrue(Files.exists(backup.resolve("manifest.json")));
            }
            assertEquals(10, repository.loadByKey("player").orElseThrow().data().coins());
        }
    }

    @Test
    void backupRotationKeepsExactlyConfiguredMaximum() throws Exception {
        try (ProgressRepository repository = ProgressRepositoryTestSupport.repository(temporaryRoot)) {
            for (int index = 0; index < ProgressRepository.MAX_BACKUP_FOLDERS + 2; index++) {
                Files.createDirectories(repository.backupsRoot().resolve(String.format("2023-01-01_00-00-%02d_%d", index, index)));
            }
            repository.backup(List.of(ProgressRepositoryTestSupport.snapshot("player", 1, 10)), 1);
            assertTrue(repository.flush(Duration.ofSeconds(10)));

            try (Stream<Path> backups = Files.list(repository.backupsRoot())) {
                assertEquals(ProgressRepository.MAX_BACKUP_FOLDERS,
                        backups.filter(Files::isDirectory)
                                .filter(path -> !path.getFileName().toString().startsWith("."))
                                .count());
            }
        }
    }

    @Test
    void failedBackupDoesNotModifyActiveProfile() throws Exception {
        com.makar.tacticaltablet.storage.AtomicFileStore failingManifestStore =
                new com.makar.tacticaltablet.storage.AtomicFileStore((source, target, options) -> {
                    if (target.getFileName().toString().equals("manifest.json")) {
                        throw new java.io.IOException("injected backup failure");
                    }
                    Files.move(source, target, options);
                });
        try (ProgressRepository repository = new ProgressRepository(
                temporaryRoot,
                ProgressRepositoryTestSupport.CONFIGURATION,
                ProgressRepositoryTestSupport.CLOCK,
                failingManifestStore,
                ProgressRepository.RepositoryLog.noop(),
                64
        )) {
            repository.initialize();
            ProgressSnapshot active = ProgressRepositoryTestSupport.snapshot("player", 1, 10);
            assertEquals(DurableSaveResult.Status.WRITTEN, await(repository.save(active, false)).status());

            repository.backup(List.of(active), 1);
            assertTrue(repository.flush(Duration.ofSeconds(10)));

            assertEquals(10, repository.loadByKey("player").orElseThrow().data().coins());
            assertTrue(Files.exists(repository.playerFile("player")));
        }
    }

    private static DurableSaveResult await(SaveTicket ticket) throws Exception {
        return ticket.completion().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
