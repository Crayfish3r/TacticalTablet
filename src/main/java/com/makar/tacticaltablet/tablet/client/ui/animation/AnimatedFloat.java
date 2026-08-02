package com.makar.tacticaltablet.tablet.client.ui.animation;

/** A frame-rate-independent scalar animation driven by caller-provided delta time. */
public final class AnimatedFloat {
    private static final float MAX_DELTA_SECONDS = 0.1F;
    private static final float FINISHED_EPSILON = 0.001F;

    private float value;
    private float target;
    private float start;
    private float elapsedSeconds;
    private final float durationSeconds;

    public AnimatedFloat(float initialValue, float durationSeconds) {
        if (!Float.isFinite(initialValue)) throw new IllegalArgumentException("initialValue must be finite");
        if (!Float.isFinite(durationSeconds) || durationSeconds <= 0.0F) {
            throw new IllegalArgumentException("durationSeconds must be finite and positive");
        }
        this.value = initialValue;
        this.target = initialValue;
        this.start = initialValue;
        this.durationSeconds = durationSeconds;
    }

    public float value() {
        return value;
    }

    public float target() {
        return target;
    }

    public void setTarget(float target) {
        if (!Float.isFinite(target)) throw new IllegalArgumentException("target must be finite");
        if (this.target == target) return;
        this.start = value;
        this.target = target;
        this.elapsedSeconds = 0.0F;
    }

    public void snapTo(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("value must be finite");
        this.value = value;
        this.target = value;
        this.start = value;
        this.elapsedSeconds = 0.0F;
    }

    public void update(float deltaSeconds) {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds <= 0.0F || isFinished()) return;
        elapsedSeconds += Math.min(deltaSeconds, MAX_DELTA_SECONDS);
        float progress = Math.min(1.0F, elapsedSeconds / durationSeconds);
        if (progress >= 1.0F) {
            value = target;
            return;
        }
        value = start + (target - start) * Easing.easeOutCubic(progress);
        if (Math.abs(target - value) <= FINISHED_EPSILON) value = target;
    }

    public boolean isFinished() {
        return Math.abs(target - value) <= FINISHED_EPSILON;
    }
}
