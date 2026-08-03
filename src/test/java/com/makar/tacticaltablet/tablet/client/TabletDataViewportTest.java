package com.makar.tacticaltablet.tablet.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TabletDataViewportTest {
    @Test
    void computesClampedVisibleRanges() {
        assertEquals(new TabletDataViewport.VisibleRange(0, 0, 0),
                TabletDataViewport.visibleRange(0, 8, 4));
        assertEquals(new TabletDataViewport.VisibleRange(6, 10, 6),
                TabletDataViewport.visibleRange(10, 20, 4));
        assertEquals(4, TabletDataViewport.visibleRange(10, 3, 4).size());
    }

    @Test
    void computesFullOrProportionalScrollbarThumb() {
        assertEquals(new TabletDataViewport.Scrollbar(0, 58),
                TabletDataViewport.scrollbar(58, 0, 0, 16));
        TabletDataViewport.Scrollbar bottom = TabletDataViewport.scrollbar(100, 50, 50, 16);
        assertEquals(66, bottom.height());
        assertEquals(34, bottom.yOffset());
    }
}
