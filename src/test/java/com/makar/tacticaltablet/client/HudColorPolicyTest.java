package com.makar.tacticaltablet.client;

import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HudColorPolicyTest {

    @Test
    void replacesUnreadablyDarkPacketColor() {
        assertEquals(TacticalTheme.INFO, HudColorPolicy.readableAccent(0xFF020304));
    }

    @Test
    void preservesReadableRgbAndNormalizesAlpha() {
        assertEquals(0xFFFFCC44, HudColorPolicy.readableAccent(0x11FFCC44));
    }
}
