package com.makar.tacticaltablet.tablet.client.ui;

/** Screen-owned clock that produces one immutable context for each top-level render invocation. */
public final class UiFrameClock {
    private long previousFrameMillis = -1L;

    public UiFrameContext nextFrame(long nowMillis, boolean reducedMotion) {
        long safeNow = Math.max(0L, nowMillis);
        float delta = previousFrameMillis < 0L
                ? 0.0F
                : Math.max(0L, safeNow - previousFrameMillis) / 1000.0F;
        previousFrameMillis = safeNow;
        return new UiFrameContext(delta, reducedMotion);
    }

    public void reset() {
        previousFrameMillis = -1L;
    }
}
