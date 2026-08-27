package com.makar.tacticaltablet.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HudColorPolicyTest {

    @Test
    void replacesUnreadablyDarkPacketColor() {
        assertEquals(ExternalUiTheme.INFO, HudColorPolicy.readableAccent(0xFF020304));
    }

    @Test
    void preservesReadableRgbAndNormalizesAlpha() {
        assertEquals(0xFFFFCC44, HudColorPolicy.readableAccent(0x11FFCC44));
    }
}
