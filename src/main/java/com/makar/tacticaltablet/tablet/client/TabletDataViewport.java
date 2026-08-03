package com.makar.tacticaltablet.tablet.client;

/** Pure list and scrollbar geometry shared by tablet data pages. */
final class TabletDataViewport {
    private TabletDataViewport() {
    }

    static VisibleRange visibleRange(int itemCount, int requestedOffset, int capacity) {
        int safeCount = Math.max(0, itemCount);
        int safeCapacity = Math.max(1, capacity);
        int maximum = Math.max(0, safeCount - safeCapacity);
        int start = Math.max(0, Math.min(maximum, requestedOffset));
        return new VisibleRange(start, Math.min(safeCount, start + safeCapacity), maximum);
    }

    static Scrollbar scrollbar(int height, int scroll, int maximumScroll, int minimumThumbHeight) {
        int safeHeight = Math.max(1, height);
        int safeMaximum = Math.max(0, maximumScroll);
        if (safeMaximum == 0) return new Scrollbar(0, safeHeight);
        int thumbHeight = Math.max(Math.min(safeHeight, minimumThumbHeight),
                safeHeight * safeHeight / (safeHeight + safeMaximum));
        int clampedScroll = Math.max(0, Math.min(safeMaximum, scroll));
        int thumbY = (safeHeight - thumbHeight) * clampedScroll / safeMaximum;
        return new Scrollbar(thumbY, thumbHeight);
    }

    record VisibleRange(int startInclusive, int endExclusive, int maximumOffset) {
        int size() {
            return endExclusive - startInclusive;
        }
    }

    record Scrollbar(int yOffset, int height) {
    }
}
