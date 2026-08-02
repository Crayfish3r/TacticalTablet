package com.makar.tacticaltablet.tablet.client.ui.animation;

public final class Easing {
    private Easing() {
    }

    public static float linear(float value) {
        return clamp01(value);
    }

    public static float easeOutCubic(float value) {
        float t = clamp01(value);
        float inverse = 1.0F - t;
        return 1.0F - inverse * inverse * inverse;
    }

    public static float easeInOutCubic(float value) {
        float t = clamp01(value);
        if (t < 0.5F) return 4.0F * t * t * t;
        float inverse = -2.0F * t + 2.0F;
        return 1.0F - inverse * inverse * inverse / 2.0F;
    }

    public static float smoothStep(float value) {
        float t = clamp01(value);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
