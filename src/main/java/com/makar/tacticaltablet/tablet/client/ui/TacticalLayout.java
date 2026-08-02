package com.makar.tacticaltablet.tablet.client.ui;

public final class TacticalLayout {
    private TacticalLayout() {
    }

    public static int centerX(int screenWidth, int width) {
        return Math.max(0, (screenWidth - Math.max(0, width)) / 2);
    }

    public static int centerY(int screenHeight, int height) {
        return Math.max(0, (screenHeight - Math.max(0, height)) / 2);
    }

    public static Rect centeredPanel(int screenWidth, int screenHeight, int preferredWidth, int preferredHeight) {
        int margin = TacticalTheme.SPACING;
        int width = Math.min(Math.max(0, preferredWidth), Math.max(0, screenWidth - margin * 2));
        int height = Math.min(Math.max(0, preferredHeight), Math.max(0, screenHeight - margin * 2));
        return new Rect(centerX(screenWidth, width), centerY(screenHeight, height), width, height);
    }

    public static Rect inset(Rect bounds, int left, int top, int right, int bottom) {
        int x = bounds.x() + Math.max(0, left);
        int y = bounds.y() + Math.max(0, top);
        int width = Math.max(0, bounds.width() - Math.max(0, left) - Math.max(0, right));
        int height = Math.max(0, bounds.height() - Math.max(0, top) - Math.max(0, bottom));
        return new Rect(x, y, width, height);
    }

    public static Rect tabletScreen(Rect tabletBounds, int left, int top, int right, int bottom) {
        return inset(tabletBounds, left, top, right, bottom);
    }

    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (width < 0 || height < 0) throw new IllegalArgumentException("Rect dimensions cannot be negative");
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }
    }
}
