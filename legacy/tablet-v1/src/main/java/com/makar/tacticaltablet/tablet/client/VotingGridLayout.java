package com.makar.tacticaltablet.tablet.client;

final class VotingGridLayout {
    static final int LEFT = 0;
    static final int RIGHT = 1;
    static final int UP = 2;
    static final int DOWN = 3;

    private VotingGridLayout() {
    }

    static int columnsFor(int availableWidth, int minimumCellWidth, int gap, int itemCount, int maximumColumns) {
        if (itemCount <= 0) return 1;
        int safeGap = Math.max(0, gap);
        int safeMinimum = Math.max(1, minimumCellWidth);
        int fit = Math.max(1, (Math.max(0, availableWidth) + safeGap) / (safeMinimum + safeGap));
        return Math.max(1, Math.min(Math.min(itemCount, Math.max(1, maximumColumns)), fit));
    }

    static int rowCount(int itemCount, int columns) {
        if (itemCount <= 0) return 0;
        return (itemCount + Math.max(1, columns) - 1) / Math.max(1, columns);
    }

    static int clampScrollRow(int requestedRow, int itemCount, int columns, int visibleRows) {
        int maximum = Math.max(0, rowCount(itemCount, columns) - Math.max(1, visibleRows));
        return Math.max(0, Math.min(maximum, requestedRow));
    }

    static int moveIndex(int current, int itemCount, int columns, int direction) {
        if (itemCount <= 0) return -1;
        int safeColumns = Math.max(1, columns);
        int index = current < 0 || current >= itemCount ? 0 : current;
        return switch (direction) {
            case LEFT -> index % safeColumns == 0 ? index : index - 1;
            case RIGHT -> index + 1 < itemCount && index % safeColumns < safeColumns - 1 ? index + 1 : index;
            case UP -> Math.max(0, index - safeColumns);
            case DOWN -> Math.min(itemCount - 1, index + safeColumns);
            default -> index;
        };
    }
}
