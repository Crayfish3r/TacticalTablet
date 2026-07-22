package com.makar.tacticaltablet.tablet.client;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScrollableGridLayoutTest {
    @Test
    void computesRowsAndMaximumScrollForRequestedCardCounts() {
        Map<Integer, Integer> expectedRows = Map.of(0, 0, 1, 1, 7, 4, 8, 4, 9, 5, 16, 8, 30, 15, 40, 20);
        for (Map.Entry<Integer, Integer> entry : expectedRows.entrySet()) {
            assertEquals(entry.getValue(), ScrollableGridLayout.rowCount(entry.getKey()), "count=" + entry.getKey());
            assertEquals(Math.max(0, entry.getValue() - 4),
                    ScrollableGridLayout.maxScrollRows(entry.getKey()), "count=" + entry.getKey());
        }
    }

    @Test
    void clampsScrollAndMapsIndexToRowColumn() {
        assertEquals(0, ScrollableGridLayout.clampScrollRows(-10, 9));
        assertEquals(1, ScrollableGridLayout.clampScrollRows(10, 9));
        assertEquals(4, ScrollableGridLayout.rowForIndex(9));
        assertEquals(1, ScrollableGridLayout.columnForIndex(9));
        assertEquals(new ScrollableGridLayout.Position(234, 46),
                ScrollableGridLayout.positionForIndex(9, 94, 46, 4));
    }

    @Test
    void visibilityHasNoEightItemLimit() {
        assertFalse(ScrollableGridLayout.isVisible(8, 0, 40));
        assertTrue(ScrollableGridLayout.isVisible(8, 1, 40));
        assertTrue(ScrollableGridLayout.isVisible(39, 16, 40));
    }
}
