package com.makar.tacticaltablet.game;

import java.util.Objects;
import java.util.UUID;

final class MatchAdmissionWindow {
    private static final int DEFAULT_LATE_JOIN_CUTOFF_SECONDS = 600;
    private static final long TICKS_PER_SECOND = 20L;

    private final long cutoffTicks;

    private UUID matchId;
    private long startedAtTick;
    private long deadlineTick;
    private long currentTick;

    MatchAdmissionWindow() {
        this(DEFAULT_LATE_JOIN_CUTOFF_SECONDS);
    }

    MatchAdmissionWindow(int cutoffSeconds) {
        if (cutoffSeconds <= 0) {
            throw new IllegalArgumentException("cutoffSeconds must be positive");
        }
        this.cutoffTicks = Math.multiplyExact(
                (long) cutoffSeconds,
                TICKS_PER_SECOND
        );
    }

    synchronized boolean open(UUID nextMatchId, long startTick) {
        Objects.requireNonNull(nextMatchId, "nextMatchId");
        if (nextMatchId.equals(matchId)) {
            return false;
        }
        matchId = nextMatchId;
        startedAtTick = startTick;
        deadlineTick = saturatedAdd(startTick, cutoffTicks);
        currentTick = startTick;
        return true;
    }

    synchronized void advance(long tick) {
        currentTick = tick;
    }

    synchronized boolean clear(UUID expectedMatchId) {
        if (expectedMatchId != null && !expectedMatchId.equals(matchId)) {
            return false;
        }
        boolean hadWindow = matchId != null;
        matchId = null;
        startedAtTick = 0L;
        deadlineTick = 0L;
        currentTick = 0L;
        return hadWindow;
    }

    synchronized Snapshot snapshot() {
        if (matchId == null) {
            return Snapshot.closed(currentTick);
        }
        return new Snapshot(
                matchId,
                startedAtTick,
                deadlineTick,
                currentTick,
                currentTick < deadlineTick
        );
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    record Snapshot(
            UUID matchId,
            long startedAtTick,
            long deadlineTick,
            long currentTick,
            boolean open
    ) {
        private static Snapshot closed(long currentTick) {
            return new Snapshot(null, 0L, 0L, currentTick, false);
        }

        boolean belongsTo(UUID expectedMatchId) {
            return matchId != null && matchId.equals(expectedMatchId);
        }
    }
}
