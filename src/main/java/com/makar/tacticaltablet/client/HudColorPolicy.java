package com.makar.tacticaltablet.client;

/** Keeps server-provided HUD accents legible on the dark tactical surface. */
final class HudColorPolicy {
    private HudColorPolicy() {
    }

    static int readableAccent(int color) {
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        int luminance = (red * 299 + green * 587 + blue * 114) / 1000;
        return luminance < 88 ? ExternalUiTheme.INFO : 0xFF000000 | color & 0x00FFFFFF;
    }
}
