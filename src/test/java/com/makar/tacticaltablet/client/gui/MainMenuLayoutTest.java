package com.makar.tacticaltablet.client.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainMenuLayoutTest {

    private static final int TEXTURE_WIDTH = 1672;
    private static final int TEXTURE_HEIGHT = 941;
    private static final float BACKGROUND_ZOOM = 1.05F;

    @Test
    void layoutStaysCenteredAndNonOverlappingAcrossRepresentativeGuiSizes() {
        assertLayout(1280, 720);
        assertLayout(640, 360);
        assertLayout(427, 240);
        assertLayout(320, 180);
    }

    @Test
    void coverOverscanAlwaysContainsConfiguredParallaxAtCommonAspectRatios() {
        assertOverscan(1280, 720, 6, 4);
        assertOverscan(640, 360, 6, 4);
        assertOverscan(427, 240, 6, 4);
        assertOverscan(320, 180, 6, 4);
        assertOverscan(640, 480, 6, 4);
        assertOverscan(854, 240, 6, 4);
    }

    private static void assertLayout(int width, int height) {
        CustomMainMenu.MenuLayout layout = CustomMainMenu.MenuLayout.calculate(width, height);
        Rect rules = new Rect(
                layout.leftX(),
                layout.topY(),
                layout.normalWidth(),
                layout.normalHeight()
        );
        Rect guide = new Rect(
                layout.rightX(),
                layout.topY(),
                layout.normalWidth(),
                layout.normalHeight()
        );
        Rect play = new Rect(
                layout.playX(),
                layout.playY(),
                layout.playWidth(),
                layout.playHeight()
        );
        Rect settings = new Rect(
                layout.leftX(),
                layout.bottomY(),
                layout.normalWidth(),
                layout.normalHeight()
        );
        Rect quit = new Rect(
                layout.rightX(),
                layout.bottomY(),
                layout.normalWidth(),
                layout.normalHeight()
        );
        List<Rect> buttons = List.of(rules, guide, play, settings, quit);

        for (Rect button : buttons) {
            assertTrue(button.x >= 0);
            assertTrue(button.y >= 0);
            assertTrue(button.right() <= width);
            assertTrue(button.bottom() <= height);
        }
        for (int first = 0; first < buttons.size(); first++) {
            for (int second = first + 1; second < buttons.size(); second++) {
                assertFalse(buttons.get(first).intersects(buttons.get(second)));
            }
        }
        assertTrue(layout.playWidth() > layout.normalWidth());
        assertTrue(Math.abs(layout.playX() + layout.playWidth() / 2 - width / 2) <= 1);
    }

    private static void assertOverscan(int width, int height, int parallaxX, int parallaxY) {
        float coverScale = Math.max(
                width / (float) TEXTURE_WIDTH,
                height / (float) TEXTURE_HEIGHT
        ) * BACKGROUND_ZOOM;
        int drawWidth = (int) Math.ceil(TEXTURE_WIDTH * coverScale);
        int drawHeight = (int) Math.ceil(TEXTURE_HEIGHT * coverScale);

        assertTrue((drawWidth - width) / 2 >= parallaxX);
        assertTrue((drawHeight - height) / 2 >= parallaxY);
    }

    private record Rect(int x, int y, int width, int height) {
        private int right() {
            return x + width;
        }

        private int bottom() {
            return y + height;
        }

        private boolean intersects(Rect other) {
            return x < other.right()
                    && right() > other.x
                    && y < other.bottom()
                    && bottom() > other.y;
        }
    }
}
