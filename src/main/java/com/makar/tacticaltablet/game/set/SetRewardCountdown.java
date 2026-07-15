package com.makar.tacticaltablet.game.set;

/** Server-side one-shot countdown; client title timing is deliberately not authoritative. */
public final class SetRewardCountdown {
    private int secondsRemaining;
    private boolean transitionIssued;

    public void resume(int secondsRemaining) {
        this.secondsRemaining = Math.max(0, secondsRemaining);
        this.transitionIssued = false;
    }

    public boolean tickSecond() {
        if (transitionIssued) return false;
        if (secondsRemaining > 0) secondsRemaining--;
        if (secondsRemaining > 0) return false;
        transitionIssued = true;
        return true;
    }

    public int secondsRemaining() {
        return secondsRemaining;
    }

    public void reset() {
        secondsRemaining = 0;
        transitionIssued = false;
    }
}
