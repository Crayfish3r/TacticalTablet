package com.makar.tacticaltablet.tablet.client;

import java.util.Objects;

public record ButtonTextureSet(
        ButtonTextureSpec normal,
        ButtonTextureSpec hover,
        ButtonTextureSpec active,
        ButtonTextureSpec disabled
) {
    public ButtonTextureSet {
        Objects.requireNonNull(normal, "normal");
        Objects.requireNonNull(hover, "hover");
        Objects.requireNonNull(active, "active");
        Objects.requireNonNull(disabled, "disabled");
        requireMatchingDimensions(normal, hover);
        requireMatchingDimensions(normal, active);
        requireMatchingDimensions(normal, disabled);
    }

    public ButtonTextureSpec select(boolean enabled, boolean selected, boolean hovered) {
        if (!enabled) return disabled;
        if (selected) return active;
        if (hovered) return hover;
        return normal;
    }

    private static void requireMatchingDimensions(ButtonTextureSpec expected, ButtonTextureSpec actual) {
        if (expected.width() != actual.width() || expected.height() != actual.height()) {
            throw new IllegalArgumentException("Every state texture must use the same logical dimensions");
        }
    }
}
