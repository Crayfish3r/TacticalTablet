package com.makar.tacticaltablet.tablet.client.ui;

/** Immutable timing and accessibility data shared by every tactical widget in one rendered frame. */
public record UiFrameContext(float deltaSeconds, boolean reducedMotion) {
    public static final float MAX_DELTA_SECONDS = 0.1F;
    public static final UiFrameContext INITIAL = new UiFrameContext(0.0F, false);

    public UiFrameContext {
        if (!Float.isFinite(deltaSeconds)) deltaSeconds = 0.0F;
        deltaSeconds = Math.max(0.0F, Math.min(MAX_DELTA_SECONDS, deltaSeconds));
    }

    public float animationDeltaSeconds() {
        return reducedMotion ? MAX_DELTA_SECONDS : deltaSeconds;
    }
}
