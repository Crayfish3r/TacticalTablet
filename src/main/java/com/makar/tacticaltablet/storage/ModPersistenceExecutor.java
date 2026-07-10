package com.makar.tacticaltablet.storage;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Bounded, single-threaded persistence queue. Callers provide immutable payloads only;
 * the worker never needs access to Minecraft runtime objects.
 */
public final class ModPersistenceExecutor implements AutoCloseable {

    public static final int DEFAULT_MAX_PENDING_TARGETS = 512;

    public interface WriteTask {
        Path target();

        long revision();

        FileSaveResult write();
    }

    public enum SubmitStatus {
        ACCEPTED,
        COALESCED,
        STALE,
        BACKPRESSURED,
        CLOSED
    }

    public record SubmitResult(SubmitStatus status, Path target, long revision, String diagnostic) {
    }

    private record Ticket(Path target, long revision, CompletableFuture<DurableSaveResult> future) implements SaveTicket {
        @Override public java.util.concurrent.CompletionStage<DurableSaveResult> completion() { return future; }
    }

    private record PendingWrite(WriteTask task, Ticket ticket) { }

    public record Health(boolean degraded, String diagnostic) {
    }

    private final ExecutorService worker;
    private final int maxPendingTargets;
    private final Consumer<String> logger;
    private final Map<Path, PendingWrite> pending = new HashMap<>();
    private final Map<Path, Long> latestAcceptedRevision = new HashMap<>();
    private final Map<Path, Long> completedRevision = new HashMap<>();
    private final Map<Path, Health> health = new HashMap<>();
    private boolean drainScheduled;
    private boolean accepting = true;
    private long lastBackpressureLogNanos;

    public ModPersistenceExecutor(String threadName, int maxPendingTargets, Consumer<String> logger) {
        if (maxPendingTargets <= 0) throw new IllegalArgumentException("maxPendingTargets must be positive");
        this.maxPendingTargets = maxPendingTargets;
        this.logger = logger == null ? ignored -> { } : logger;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        };
        this.worker = Executors.newSingleThreadExecutor(factory);
    }

    public ModPersistenceExecutor(String threadName, Consumer<String> logger) {
        this(threadName, DEFAULT_MAX_PENDING_TARGETS, logger);
    }

    public synchronized SubmitResult submit(WriteTask task) {
        return submit(task, false);
    }

    public synchronized SaveTicket enqueueSnapshot(WriteTask task) {
        return enqueue(task, false);
    }

    public synchronized SaveTicket enqueueFinalSnapshot(WriteTask task) {
        return enqueue(task, true);
    }

    /** Accepts only the caller's final snapshots after normal intake has been closed. */
    public synchronized SubmitResult submitFinal(WriteTask task) {
        return submit(task, true);
    }

    private SubmitResult submit(WriteTask task, boolean finalSnapshot) {
        SaveTicket ticket = enqueue(task, finalSnapshot);
        DurableSaveResult immediate = ticket.completion().toCompletableFuture().getNow(null);
        if (immediate != null) {
            return switch (immediate.status()) {
                case STALE_REJECTED -> new SubmitResult(SubmitStatus.STALE, immediate.target(), immediate.revision(), immediate.diagnostic());
                case QUEUE_REJECTED -> new SubmitResult(SubmitStatus.BACKPRESSURED, immediate.target(), immediate.revision(), immediate.diagnostic());
                case EXECUTOR_STOPPED -> new SubmitResult(SubmitStatus.CLOSED, immediate.target(), immediate.revision(), immediate.diagnostic());
                default -> new SubmitResult(SubmitStatus.ACCEPTED, immediate.target(), immediate.revision(), immediate.diagnostic());
            };
        }
        return new SubmitResult(SubmitStatus.ACCEPTED, ticket.target(), ticket.revision(), "");
    }

    private Ticket enqueue(WriteTask task, boolean finalSnapshot) {
        Objects.requireNonNull(task, "task");
        Path target = Objects.requireNonNull(task.target(), "task.target").toAbsolutePath().normalize();
        Ticket ticket = new Ticket(target, task.revision(), new CompletableFuture<>());
        if (!accepting && !finalSnapshot) {
            ticket.future.complete(DurableSaveResult.of(DurableSaveResult.Status.EXECUTOR_STOPPED, target, task.revision(), "Persistence executor is shutting down", null));
            return ticket;
        }

        long accepted = latestAcceptedRevision.getOrDefault(target, Long.MIN_VALUE);
        if (task.revision() <= accepted) {
            ticket.future.complete(DurableSaveResult.of(DurableSaveResult.Status.STALE_REJECTED, target, task.revision(), "A newer snapshot is already queued or saved", null));
            return ticket;
        }

        PendingWrite previous = pending.get(target);
        if (previous == null && pending.size() >= maxPendingTargets) {
            logBackpressure(target);
            ticket.future.complete(DurableSaveResult.of(DurableSaveResult.Status.QUEUE_REJECTED, target, task.revision(), "Bounded persistence queue is full", null));
            return ticket;
        }

        pending.put(target, new PendingWrite(task, ticket));
        if (previous != null) previous.ticket.future.complete(DurableSaveResult.of(DurableSaveResult.Status.SUPERSEDED, target, previous.ticket.revision(), "Superseded by revision " + task.revision(), null));
        latestAcceptedRevision.put(target, task.revision());
        scheduleDrain();
        return ticket;
    }

    public synchronized int pendingTargets() {
        return pending.size();
    }

    public synchronized Health health(Path target) {
        return health.getOrDefault(target.toAbsolutePath().normalize(), new Health(false, ""));
    }

    public synchronized long completedRevision(Path target) {
        return completedRevision.getOrDefault(target.toAbsolutePath().normalize(), Long.MIN_VALUE);
    }

    public synchronized void stopAccepting() {
        accepting = false;
    }

    public boolean flush(Duration timeout) {
        long deadline = System.nanoTime() + Math.max(0L, timeout.toNanos());
        synchronized (this) {
            while (drainScheduled || !pending.isEmpty()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) return false;
                try {
                    TimeUnit.NANOSECONDS.timedWait(this, remaining);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void close() {
        stopAccepting();
        synchronized (this) {
            for (PendingWrite pendingWrite : pending.values()) {
                pendingWrite.ticket().future().complete(DurableSaveResult.of(
                        DurableSaveResult.Status.EXECUTOR_STOPPED, pendingWrite.ticket().target(),
                        pendingWrite.ticket().revision(), "Persistence executor stopped before write", null));
            }
            pending.clear();
            notifyAll();
        }
        worker.shutdown();
    }

    private synchronized void scheduleDrain() {
        if (drainScheduled) return;
        drainScheduled = true;
        worker.execute(this::drain);
    }

    private void drain() {
        while (true) {
            WriteTask task;
            Ticket inFlightTicket;
            synchronized (this) {
                if (pending.isEmpty()) {
                    drainScheduled = false;
                    notifyAll();
                    return;
                }
                Map.Entry<Path, PendingWrite> entry = pending.entrySet().iterator().next();
                PendingWrite pendingWrite = entry.getValue();
                task = pendingWrite.task();
                pending.remove(entry.getKey());
                // The ticket follows this concrete task even after it leaves the coalescing map.
                inFlightTicket = pendingWrite.ticket();
            }

            try {
                FileSaveResult result = task.write();
                synchronized (this) {
                    if (result.status() == FileSaveResult.Status.SUCCESS) {
                        Path target = task.target().toAbsolutePath().normalize();
                        completedRevision.merge(target, task.revision(), Math::max);
                        health.remove(target);
                        inFlightTicket.future.complete(DurableSaveResult.of(DurableSaveResult.Status.WRITTEN, target, task.revision(), "", null));
                    } else {
                        health.put(task.target().toAbsolutePath().normalize(), new Health(true, result.diagnostic()));
                        logger.accept("Persistence write failed for " + task.target() + ": " + result.diagnostic());
                        inFlightTicket.future.complete(DurableSaveResult.of(DurableSaveResult.Status.FAILED, task.target(), task.revision(), result.diagnostic(), result.exception().orElse(null)));
                    }
                }
            } catch (Throwable exception) {
                synchronized (this) {
                    health.put(task.target().toAbsolutePath().normalize(), new Health(true, exception.getMessage()));
                }
                logger.accept("Persistence worker crashed a write for " + task.target() + ": " + exception);
                inFlightTicket.future.complete(DurableSaveResult.of(DurableSaveResult.Status.FAILED, task.target(), task.revision(), exception.getMessage(), exception));
            }
        }
    }

    private void logBackpressure(Path target) {
        long now = System.nanoTime();
        if (now - lastBackpressureLogNanos >= TimeUnit.SECONDS.toNanos(30)) {
            lastBackpressureLogNanos = now;
            logger.accept("Persistence queue is full; retaining dirty snapshot for retry: " + target);
        }
    }
}
