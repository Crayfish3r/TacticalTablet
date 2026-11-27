package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.progression.ClassTier;

import java.util.Objects;

public final class ClassButtonStyle {
    private static final float HOVER_BRIGHTNESS = 1.08F;
    private static final float DISABLED_BRIGHTNESS = 0.68F;
    private static final float DISABLED_ALPHA = 0.65F;

    private ClassButtonStyle() {
    }

    public static ClassTier actualTier(int fixedLevel, int synchronizedTier) {
        return ClassTier.clamp(fixedLevel >= 0 ? fixedLevel : synchronizedTier);
    }

    public static Tint tint(ClassTier tier, boolean enabled, boolean hovered) {
        Objects.requireNonNull(tier, "tier");
        int color = color(tier);
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        float brightness = enabled ? hovered ? HOVER_BRIGHTNESS : 1.0F : DISABLED_BRIGHTNESS;
        float alpha = enabled ? 1.0F : DISABLED_ALPHA;
        return new Tint(
                clamp(red / 255.0F * brightness),
                clamp(green / 255.0F * brightness),
                clamp(blue / 255.0F * brightness),
                alpha
        );
    }

    public static int color(ClassTier tier) {
        Objects.requireNonNull(tier, "tier");
        switch (tier) {
            case BASIC -> {
                return 0xFF72D68A;
            }
            case RARE -> {
                return 0xFF5B8DEF;
            }
            case EPIC -> {
                return 0xFFA56BE8;
            }
            case LEGEND -> {
                return 0xFFE7C76A;
            }
            case MONSTER -> {
                return 0xFFD87575;
            }
            default -> throw new IllegalStateException("Unexpected tier " + tier);
        }
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public record Tint(float red, float green, float blue, float alpha) {
    }
}
