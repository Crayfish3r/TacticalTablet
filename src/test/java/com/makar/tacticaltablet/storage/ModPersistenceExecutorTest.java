package com.makar.tacticaltablet.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModPersistenceExecutorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void coalescesSeveralRevisionsAndWritesTheNewestSnapshot() throws Exception {
        Path target = temporaryDirectory.resolve("player.json");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ModPersistenceExecutor executor = new ModPersistenceExecutor("test-persistence", 4, ignored -> { })) {
            executor.submit(task(target, 1, () -> {
                started.countDown();
                await(release);
                Files.writeString(target, "old");
            }));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertEquals(ModPersistenceExecutor.SubmitStatus.ACCEPTED,
                    executor.submit(task(target, 2, () -> Files.writeString(target, "new"))).status());
            release.countDown();
            assertTrue(executor.flush(Duration.ofSeconds(2)));
            assertEquals("new", Files.readString(target));
        }
    }

    @Test
    void olderSnapshotCannotOverwriteNewerRevision() throws Exception {
        Path target = temporaryDirectory.resolve("player.json");
        try (ModPersistenceExecutor executor = new ModPersistenceExecutor("test-persistence", 4, ignored -> { })) {
            executor.submit(task(target, 2, () -> Files.writeString(target, "new")));
            assertEquals(ModPersistenceExecutor.SubmitStatus.STALE,
                    executor.submit(task(target, 1, () -> Files.writeString(target, "old"))).status());
            assertTrue(executor.flush(Duration.ofSeconds(2)));
            assertEquals("new", Files.readString(target));
        }
    }

    @Test
    void enforcesBoundedQueueWithoutDiscardingExistingLatestSnapshot() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ModPersistenceExecutor executor = new ModPersistenceExecutor("test-persistence", 1, ignored -> { })) {
            executor.submit(task(temporaryDirectory.resolve("one.json"), 1, () -> {
                started.countDown();
                await(release);
            }));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            executor.submit(task(temporaryDirectory.resolve("two.json"), 1, () -> { }));
            assertEquals(ModPersistenceExecutor.SubmitStatus.BACKPRESSURED,
                    executor.submit(task(temporaryDirectory.resolve("three.json"), 1, () -> { })).status());
            release.countDown();
            assertTrue(executor.flush(Duration.ofSeconds(2)));
        }
    }

    @Test
    void writerExceptionDoesNotKillExecutorAndLaterSuccessClearsHealth() {
        Path target = temporaryDirectory.resolve("player.json");
        try (ModPersistenceExecutor executor = new ModPersistenceExecutor("test-persistence", 4, ignored -> { })) {
            executor.submit(task(target, 1, () -> { throw new IllegalStateException("boom"); }));
            assertTrue(executor.flush(Duration.ofSeconds(2)));
            assertTrue(executor.health(target).degraded());
            executor.submit(task(target, 2, () -> Files.writeString(target, "recovered")));
            assertTrue(executor.flush(Duration.ofSeconds(2)));
            assertFalse(executor.health(target).degraded());
        }
    }

    @Test
    void shutdownFlushAndTimeoutAreBounded() throws Exception {
        Path target = temporaryDirectory.resolve("player.json");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ModPersistenceExecutor executor = new ModPersistenceExecutor("test-persistence", 4, ignored -> { })) {
            executor.submit(task(target, 1, () -> {
                started.countDown();
                await(release);
                Files.writeString(target, "final");
            }));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            executor.stopAccepting();
            assertFalse(executor.flush(Duration.ofMillis(20)));
            assertEquals(ModPersistenceExecutor.SubmitStatus.CLOSED,
                    executor.submit(task(target, 2, () -> { })).status());
            assertEquals(ModPersistenceExecutor.SubmitStatus.ACCEPTED,
                    executor.submitFinal(task(target, 3, () -> Files.writeString(target, "final-snapshot"))).status());
            release.countDown();
            assertTrue(executor.flush(Duration.ofSeconds(2)));
            assertEquals("final-snapshot", Files.readString(target));
        }
    }

    @Test
    void backupCoordinatorProvidesMutualExclusion() {
        BackupCoordinator coordinator = new BackupCoordinator();
        assertTrue(coordinator.tryStart());
        assertFalse(coordinator.tryStart());
        coordinator.finish();
        assertTrue(coordinator.tryStart());
    }

    @Test
    void ticketsCompleteForWrittenSupersededAndQueueRejectedSnapshots() throws Exception {
        Path target = temporaryDirectory.resolve("player.json");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ModPersistenceExecutor executor = new ModPersistenceExecutor("test-persistence", 1, ignored -> { })) {
            executor.submit(task(temporaryDirectory.resolve("blocking.json"), 1, () -> {
                started.countDown();
                await(release);
            }));
            assertTrue(started.await(2, TimeUnit.SECONDS));

            SaveTicket first = executor.enqueueSnapshot(task(target, 1, () -> Files.writeString(target, "old")));
            SaveTicket second = executor.enqueueSnapshot(task(target, 2, () -> Files.writeString(target, "new")));
            SaveTicket rejected = executor.enqueueSnapshot(task(temporaryDirectory.resolve("overflow.json"), 1, () -> { }));

            assertEquals(DurableSaveResult.Status.SUPERSEDED,
                    first.completion().toCompletableFuture().get(2, TimeUnit.SECONDS).status());
            assertEquals(DurableSaveResult.Status.QUEUE_REJECTED,
                    rejected.completion().toCompletableFuture().get(2, TimeUnit.SECONDS).status());
            release.countDown();
            assertEquals(DurableSaveResult.Status.WRITTEN,
                    second.completion().toCompletableFuture().get(2, TimeUnit.SECONDS).status());
            assertEquals("new", Files.readString(target));
        }
    }

    private static ModPersistenceExecutor.WriteTask task(Path target, long revision, IoAction action) {
        return new ModPersistenceExecutor.WriteTask() {
            @Override public Path target() { return target; }
            @Override public long revision() { return revision; }
            @Override public FileSaveResult write() {
                try {
                    action.run();
                    return FileSaveResult.success(target);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }
        };
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("test latch timed out");
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws Exception;
    }
}
