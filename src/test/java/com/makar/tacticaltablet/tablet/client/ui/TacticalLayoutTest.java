package com.makar.tacticaltablet.tablet.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalLayoutTest {
    @Test
    void centeredPanelStaysInsideSmallScreens() {
        TacticalLayout.Rect panel = TacticalLayout.centeredPanel(120, 80, 380, 220);

        assertEquals(TacticalTheme.SPACING, panel.x());
        assertEquals(TacticalTheme.SPACING, panel.y());
        assertEquals(104, panel.width());
        assertEquals(64, panel.height());
        assertTrue(panel.right() <= 120);
        assertTrue(panel.bottom() <= 80);
    }

    @Test
    void tabletInsetsCannotProduceNegativeDimensions() {
        TacticalLayout.Rect screen = TacticalLayout.tabletScreen(
                new TacticalLayout.Rect(10, 20, 30, 40), 20, 20, 20, 30);

        assertEquals(30, screen.x());
        assertEquals(40, screen.y());
        assertEquals(0, screen.width());
        assertEquals(0, screen.height());
    }

    @Test
    void colorUtilitiesInterpolateArgbAndReplaceAlpha() {
        assertEquals(0xFF808080, TacticalUi.lerpArgb(0xFF000000, 0xFFFFFFFF, 0.5F));
        assertEquals(0x40123456, TacticalUi.withAlpha(0xFF123456, 0x40));
    }
}
