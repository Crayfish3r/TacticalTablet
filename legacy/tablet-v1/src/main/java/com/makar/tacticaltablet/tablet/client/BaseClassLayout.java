package com.makar.tacticaltablet.tablet.client;

/** Geometry for the base-class slice only. Legacy shop/VIP grid defaults are unchanged. */
public final class BaseClassLayout {
    public static final int WIDTH = 274, HEIGHT = 150, TAB_HEIGHT = 20, TOP = 24;
    public static final int VISIBLE_ROWS = 3, ROW_HEIGHT = 40, ROW_STEP = 42;
    public static final int CARD_WIDTH = 130, GAP = 10;
    private BaseClassLayout() { }
    public static int columns(boolean progression) { return progression ? 1 : 2; }
    public static int maximumOffset(int count, boolean progression) {
        return Math.max(0, (count + columns(progression) - 1) / columns(progression) - VISIBLE_ROWS);
    }
    public static int clamp(int offset, int count, boolean progression) {
        return Math.max(0, Math.min(maximumOffset(count, progression), offset));
    }
    public static int reveal(int index, int offset, int count, boolean progression) {
        if (index < 0 || index >= count) return clamp(offset, count, progression);
        int row = index / columns(progression);
        int next = clamp(offset, count, progression);
        if (row < next) next = row;
        if (row >= next + VISIBLE_ROWS) next = row - VISIBLE_ROWS + 1;
        return clamp(next, count, progression);
    }
    public static int move(int index, int count, boolean progression, int dx, int dy) {
        if (count <= 0) return -1;
        int safe = Math.max(0, Math.min(count - 1, index));
        int columns = columns(progression);
        if (dx != 0 && (safe % columns + dx < 0 || safe % columns + dx >= columns)) return safe;
        int next = safe + dx + dy * columns;
        return next >= 0 && next < count ? next : safe;
    }
    public static int tabIndex(int index, int count, boolean backwards) {
        int next = index + (backwards ? -1 : 1);
        return next >= 0 && next < count ? next : -1;
    }
}
