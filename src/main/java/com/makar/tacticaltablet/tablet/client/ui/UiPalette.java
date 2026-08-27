package com.makar.tacticaltablet.tablet.client.ui;

/** Color-only tokens that can be scoped without changing tablet layout or behavior. */
public record UiPalette(
        int backdrop,
        int surface,
        int surfaceRaised,
        int surfaceHover,
        int surfaceSelected,
        int surfaceDisabled,
        int border,
        int borderHover,
        int borderDisabled,
        int accent,
        int accentMuted,
        int accentDark,
        int textPrimary,
        int textSecondary,
        int textDisabled,
        int success,
        int warning,
        int danger,
        int info,
        int shadow
) {
    public static UiPalette tabletDefault() {
        return new UiPalette(
                TacticalTheme.BACKDROP,
                TacticalTheme.SURFACE,
                TacticalTheme.SURFACE_RAISED,
                TacticalTheme.SURFACE_HOVER,
                TacticalTheme.SURFACE_SELECTED,
                TacticalTheme.SURFACE_DISABLED,
                TacticalTheme.BORDER,
                TacticalTheme.BORDER_HOVER,
                TacticalTheme.BORDER_DISABLED,
                TacticalTheme.ACCENT,
                TacticalTheme.ACCENT_MUTED,
                TacticalTheme.ACCENT_DARK,
                TacticalTheme.TEXT_PRIMARY,
                TacticalTheme.TEXT_SECONDARY,
                TacticalTheme.TEXT_DISABLED,
                TacticalTheme.SUCCESS,
                TacticalTheme.WARNING,
                TacticalTheme.DANGER,
                TacticalTheme.INFO,
                TacticalTheme.SHADOW
        );
    }
}
