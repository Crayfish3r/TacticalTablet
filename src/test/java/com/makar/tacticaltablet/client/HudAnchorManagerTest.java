package com.makar.tacticaltablet.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;

class HudAnchorManagerTest {

    @Test
    void keepsAllAnchorsInsideNarrowSafeArea() {
        assertInside(HudAnchorManager.hotbarSide(320, 180, 120, 18), 320, 180);
        assertInside(HudAnchorManager.topCenter(320, 180, 280, 40), 320, 180);
        assertInside(HudAnchorManager.spectatorHint(320, 180, 260, 17), 320, 180);
        HudAnchorManager.Rect hint = HudAnchorManager.spectatorHint(320, 180, 260, 17);
        assertInside(HudAnchorManager.spectatorPanel(320, 180, 250, 45, hint), 320, 180);
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
        HudAnchorManager.Rect notice = HudAnchorManager.topCenter(320, 180, 280, 37);
        HudAnchorManager.Rect lives = HudAnchorManager.hotbarSide(320, 180, 120, 18);
        HudAnchorManager.Rect spectator = HudAnchorManager.spectatorHint(320, 180, 260, 17);
        HudAnchorManager.Rect panel = HudAnchorManager.spectatorPanel(320, 180, 250, 45, spectator);

        assertFalse(notice.intersects(lives));
        assertFalse(notice.intersects(spectator));
        assertFalse(lives.intersects(spectator));
        assertFalse(panel.intersects(spectator));
        assertFalse(panel.intersects(lives));
        assertFalse(panel.intersects(notice));
    }

    @Test
    void spectatorPanelStaysAboveHintAcrossViewports() {
        for (int[] viewport : new int[][]{{320, 180}, {426, 240}, {640, 360}}) {
            HudAnchorManager.Rect hint = HudAnchorManager.spectatorHint(viewport[0], viewport[1], 260, 17);
            HudAnchorManager.Rect panel = HudAnchorManager.spectatorPanel(viewport[0], viewport[1], 250, 45, hint);
            assertInside(panel, viewport[0], viewport[1]);
            assertFalse(panel.intersects(hint));
            assertTrue(panel.y() + panel.height() < hint.y());
        }
    }

    @Test
    void staminaBarsAvoidVanillaAndTacticalReservationsAcrossGuiScaledViewports() {
        for (int[] viewport : new int[][]{{320, 180}, {426, 240}, {640, 360}, {960, 540}}) {
            int width = viewport[0];
            int height = viewport[1];
            HudAnchorManager.Rect vanilla = new HudAnchorManager.Rect(
                    Math.max(0, width / 2 - 101), Math.max(0, height - 70), Math.min(202, width), 70);
            HudAnchorManager.Rect killFeed = new HudAnchorManager.Rect(Math.max(0, width - 230), 4,
                    Math.min(230, width), 112);
            HudAnchorManager.Rect stamina = HudAnchorManager.staminaBars(width, height, 84, 33,
                    HudAnchorManager.Side.AUTO, 0, 0, List.of(vanilla, killFeed));
            assertInside(stamina, width, height);
            assertFalse(stamina.intersects(vanilla));
            assertFalse(stamina.intersects(killFeed));
        }
    }

    private static void assertInside(HudAnchorManager.Rect rect, int width, int height) {
        assertTrue(rect.x() >= HudAnchorManager.SAFE_MARGIN);
        assertTrue(rect.y() >= HudAnchorManager.SAFE_MARGIN);
        assertTrue(rect.x() + rect.width() <= width - HudAnchorManager.SAFE_MARGIN);
        assertTrue(rect.y() + rect.height() <= height - HudAnchorManager.SAFE_MARGIN);
    }
}
