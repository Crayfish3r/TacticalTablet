package com.makar.tacticaltablet.storage;

import java.util.concurrent.atomic.AtomicBoolean;

/** Ensures that a mod backup has at most one active writer. */
public final class BackupCoordinator {
    private final AtomicBoolean running = new AtomicBoolean();

    public boolean tryStart() {
        return running.compareAndSet(false, true);
    }

    public void finish() {
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }
}
