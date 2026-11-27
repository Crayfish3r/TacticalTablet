package com.makar.tacticaltablet.tablet.client.prototype;

/** Pure, monotonic-time lifecycle. Repeated use requires a physical release. */
public final class TabletAnimationController {
    public enum State { CLOSED, DRAWING, OPEN, HOLSTERING }
    private final long drawNanos;
    private final long holsterNanos;
    private State state = State.CLOSED;
    private long started;
    private boolean useHeld;
    private boolean closeAfterDraw;

    public TabletAnimationController(double drawSeconds, double holsterSeconds) {
        if (!Double.isFinite(drawSeconds) || !Double.isFinite(holsterSeconds)
                || drawSeconds <= 0 || holsterSeconds <= 0) throw new IllegalArgumentException("Invalid duration");
        drawNanos = Math.round(drawSeconds * 1_000_000_000L);
        holsterNanos = Math.round(holsterSeconds * 1_000_000_000L);
    }

    public void pressUse(long now) {
        update(now);
        if (useHeld) return;
        useHeld = true;
        if (state == State.CLOSED) {
            state = State.DRAWING;
            started = now;
        } else if (state == State.OPEN) {
            requestClose(now);
        }
    }

    public void releaseUse() { useHeld = false; }

    public void requestClose(long now) {
        update(now);
        // Finish drawing before holstering, preserving the authored poses without a jump.
        if (state == State.DRAWING) closeAfterDraw = true;
        if (state == State.OPEN) {
            state = State.HOLSTERING;
            started = now;
        }
    }

    public void update(long now) {
        if (state == State.DRAWING && now - started >= drawNanos) {
            state = closeAfterDraw ? State.HOLSTERING : State.OPEN;
            started += drawNanos;
            closeAfterDraw = false;
        }
        if (state == State.HOLSTERING && now - started >= holsterNanos) state = State.CLOSED;
    }

    public State state() { return state; }
    public boolean active() { return state != State.CLOSED; }
    public String clip() { return state == State.HOLSTERING ? "holster" : "draw"; }
    public double seconds(long now) {
        if (state == State.OPEN) return drawNanos / 1_000_000_000.0;
        long length = state == State.HOLSTERING ? holsterNanos : drawNanos;
        return Math.min(length, Math.max(0, now - started)) / 1_000_000_000.0;
    }

    public void reset() {
        state = State.CLOSED;
        closeAfterDraw = false;
        // Keep the input latch until release: a held button must never reopen after reset.
    }
}
