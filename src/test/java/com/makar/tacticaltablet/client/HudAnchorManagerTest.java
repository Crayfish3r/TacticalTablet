package com.makar.tacticaltablet.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudAnchorManagerTest {

    @Test
    void keepsAllAnchorsInsideNarrowSafeArea() {
        assertInside(HudAnchorManager.hotbarSide(320, 180, 120, 18), 320, 180);
        assertInside(HudAnchorManager.topCenter(320, 180, 280, 40), 320, 180);
        assertInside(HudAnchorManager.spectatorHint(320, 180, 260, 17), 320, 180);
    }

    @Test
    void movesWideCounterGroupAboveHotbar() {
        HudAnchorManager.Rect compact = HudAnchorManager.hotbarSide(640, 360, 100, 18);
        HudAnchorManager.Rect narrow = HudAnchorManager.hotbarSide(320, 180, 120, 18);

        assertTrue(compact.x() > 640 / 2);
        assertEquals(136, narrow.y());
    }

    @Test
    void tacticalZonesDoNotOverlapAtMinimumViewport() {
        HudAnchorManager.Rect notice = HudAnchorManager.topCenter(320, 180, 280, 40);
        HudAnchorManager.Rect lives = HudAnchorManager.hotbarSide(320, 180, 120, 18);
        HudAnchorManager.Rect spectator = HudAnchorManager.spectatorHint(320, 180, 260, 17);

        assertFalse(notice.intersects(lives));
        assertFalse(notice.intersects(spectator));
        assertFalse(lives.intersects(spectator));
    }

    private static void assertInside(HudAnchorManager.Rect rect, int width, int height) {
        assertTrue(rect.x() >= HudAnchorManager.SAFE_MARGIN);
        assertTrue(rect.y() >= HudAnchorManager.SAFE_MARGIN);
        assertTrue(rect.x() + rect.width() <= width - HudAnchorManager.SAFE_MARGIN);
        assertTrue(rect.y() + rect.height() <= height - HudAnchorManager.SAFE_MARGIN);
    }
}
