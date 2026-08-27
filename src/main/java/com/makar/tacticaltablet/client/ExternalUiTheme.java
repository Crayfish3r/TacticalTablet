package com.makar.tacticaltablet.client;

import com.makar.tacticaltablet.tablet.client.ui.UiPalette;

/** Palette for HUD and non-tablet screens. The tablet keeps its original TacticalTheme. */
public final class ExternalUiTheme {
    public static final int PRIMARY = 0xFF246B25;
    public static final int SECONDARY = 0xFF6B246A;

    public static final int BACKDROP = 0xD9071009;
    public static final int SURFACE = 0xF00E1B11;
    public static final int SURFACE_RAISED = 0xFF172B19;
    public static final int SURFACE_HOVER = 0xFF214A25;
    public static final int SURFACE_SELECTED = 0xFF422044;
    public static final int SURFACE_DISABLED = 0xFF141A15;
    public static final int BORDER = 0xFF355E37;
    public static final int BORDER_HOVER = SECONDARY;
    public static final int BORDER_DISABLED = 0xFF29372B;
    public static final int ACCENT = PRIMARY;
    public static final int ACCENT_MUTED = 0xFF3D8A3F;
    public static final int ACCENT_DARK = 0xFF173F18;
    public static final int TEXT_PRIMARY = 0xFFF0F5F0;
    public static final int TEXT_SECONDARY = 0xFFB7C8B8;
    public static final int TEXT_DISABLED = 0xFF6C7B6D;
    public static final int SUCCESS = 0xFF55A957;
    public static final int WARNING = 0xFFC45AB1;
    public static final int DANGER = 0xFFB63F68;
    public static final int INFO = 0xFF8E4A8D;
    public static final int SHADOW = 0x66000000;

    public static final UiPalette PALETTE = new UiPalette(
            BACKDROP, SURFACE, SURFACE_RAISED, SURFACE_HOVER, SURFACE_SELECTED,
            SURFACE_DISABLED, BORDER, BORDER_HOVER, BORDER_DISABLED, ACCENT,
            ACCENT_MUTED, ACCENT_DARK, TEXT_PRIMARY, TEXT_SECONDARY, TEXT_DISABLED,
            SUCCESS, WARNING, DANGER, INFO, SHADOW
    );

    private ExternalUiTheme() {
    }
}
