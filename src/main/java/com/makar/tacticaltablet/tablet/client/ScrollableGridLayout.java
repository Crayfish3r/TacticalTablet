package com.makar.tacticaltablet.tablet.client;

/** Pure row-based geometry for the tablet action grid. */
public final class ScrollableGridLayout {
    public static final int COLUMNS = 2;
    public static final int VISIBLE_ROWS = 4;
    public static final int CARD_WIDTH = 130;
    public static final int CARD_HEIGHT = 34;
    public static final int HORIZONTAL_GAP = 10;
    public static final int VERTICAL_GAP = 4;
    public static final int ROW_STEP = CARD_HEIGHT + VERTICAL_GAP;

    private ScrollableGridLayout() {
    }

    public static int rowCount(int itemCount) {
        if (itemCount <= 0) return 0;
        return (itemCount + COLUMNS - 1) / COLUMNS;
    }

    public static int maxScrollRows(int itemCount) {
        return Math.max(0, rowCount(itemCount) - VISIBLE_ROWS);
    }

    public static int clampScrollRows(int scrollRows, int itemCount) {
        return Math.max(0, Math.min(maxScrollRows(itemCount), scrollRows));
    }

    public static int rowForIndex(int index) {
        if (index < 0) throw new IllegalArgumentException("index must not be negative");
        return index / COLUMNS;
    }

    public static int columnForIndex(int index) {
        if (index < 0) throw new IllegalArgumentException("index must not be negative");
        return index % COLUMNS;
    }

    public static Position positionForIndex(int index, int originX, int originY, int scrollRows) {
        return new Position(
                originX + columnForIndex(index) * (CARD_WIDTH + HORIZONTAL_GAP),
                originY + (rowForIndex(index) - Math.max(0, scrollRows)) * ROW_STEP
        );
    }

    public static boolean isVisible(int index, int scrollRows, int itemCount) {
        if (index < 0 || index >= itemCount) return false;
        int clamped = clampScrollRows(scrollRows, itemCount);
        int row = rowForIndex(index);
        return row >= clamped && row < clamped + VISIBLE_ROWS;
    }

    public record Position(int x, int y) {
    }
}
