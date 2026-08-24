package com.makar.tacticaltablet.client.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainMenuLayoutTest {

    private static final int TEXTURE_WIDTH = 1920;
    private static final int TEXTURE_HEIGHT = 1080;

    @Test
    void tabletLayoutStaysCenteredAndNonOverlappingAcrossRepresentativeGuiSizes() {
        assertLayout(1280, 720);
        assertLayout(640, 360);
        assertLayout(427, 240);
        assertLayout(320, 180);
    }

    @Test
    void fullHdLayoutMatchesTheProvidedReferenceGeometry() {
        TabletMenuLayout layout = TabletMenuLayout.calculateMain(1920, 1080);

        assertTrue(Math.abs(layout.frameX() - 432) <= 1);
        assertTrue(Math.abs(layout.frameY() - 427) <= 1);
        assertTrue(Math.abs(layout.frameWidth() - 1056) <= 1);
        assertTrue(Math.abs(layout.buttonX() - 537) <= 1);
        assertTrue(Math.abs(layout.firstButtonY() - 497) <= 1);
    }

    @Test
    void backgroundCoverPreservesAspectRatioAtCommonViewports() {
        assertCover(1280, 720);
        assertCover(640, 360);
        assertCover(427, 240);
        assertCover(320, 180);
        assertCover(640, 480);
        assertCover(854, 240);
    }

    private static void assertLayout(int width, int height) {
        assertLayout(width, height, TabletMenuLayout.calculateMain(width, height));
        assertLayout(width, height, TabletMenuLayout.calculatePause(width, height));
    }

    private static void assertLayout(int width, int height, TabletMenuLayout layout) {
        Rect frame = new Rect(
                layout.frameX(),
                layout.frameY(),
                layout.frameWidth(),
                layout.frameHeight()
        );
        List<Rect> buttons = java.util.stream.IntStream.range(0, TabletMenuLayout.BUTTON_COUNT)
                .mapToObj(index -> new Rect(
                        layout.buttonX(),
                        layout.buttonY(index),
                        layout.buttonWidth(),
                        layout.buttonHeight()
                ))
                .toList();

        assertTrue(frame.x >= 0);
        assertTrue(frame.y >= 0);
        assertTrue(frame.right() <= width);
        assertTrue(frame.bottom() <= height);
        assertTrue(Math.abs(layout.frameX() + layout.frameWidth() / 2 - width / 2) <= 1);
        for (Rect button : buttons) {
            assertTrue(button.x >= frame.x);
            assertTrue(button.y >= frame.y);
            assertTrue(button.right() <= frame.right());
            assertTrue(button.bottom() <= frame.bottom());
        }
        for (int first = 0; first < buttons.size(); first++) {
            for (int second = first + 1; second < buttons.size(); second++) {
                assertFalse(buttons.get(first).intersects(buttons.get(second)));
            }
        }
        assertTrue(Math.abs(layout.buttonX() + layout.buttonWidth() / 2 - width / 2) <= 1);
    }

    private static void assertCover(int width, int height) {
        float coverScale = Math.max(
                width / (float) TEXTURE_WIDTH,
                height / (float) TEXTURE_HEIGHT
        );
        int drawWidth = (int) Math.ceil(TEXTURE_WIDTH * coverScale);
        int drawHeight = (int) Math.ceil(TEXTURE_HEIGHT * coverScale);

        assertTrue(drawWidth >= width);
        assertTrue(drawHeight >= height);
        assertTrue(Math.abs(drawWidth / (float) drawHeight - 16.0F / 9.0F) < 0.01F);
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
