package com.makar.tacticaltablet.game.zone;

final class ZoneFinalRevealScheduler {
    private final int intervalSeconds;
    private boolean active;
    private int secondsUntilNext;

    ZoneFinalRevealScheduler(int intervalSeconds) {
        this.intervalSeconds = Math.max(1, intervalSeconds);
        reset();
    }

    boolean tick(int phaseIndex, int finalRevealStartPhaseIndex, int phaseCount) {
        if (!ZonePacingPolicy.finalRevealEnabled(phaseIndex, finalRevealStartPhaseIndex, phaseCount)) {
            active = false;
            secondsUntilNext = intervalSeconds;
            return false;
        }

        if (!active) {
            active = true;
            secondsUntilNext = intervalSeconds;
        }

        secondsUntilNext--;
        if (secondsUntilNext > 0) {
            return false;
        }

        secondsUntilNext = intervalSeconds;
        return true;
    }

    void reset() {
        active = false;
        secondsUntilNext = intervalSeconds;
    }

    int secondsUntilNext() {
        return secondsUntilNext;
    }

    boolean active() {
        return active;
    }
}
