package com.makar.tacticaltablet.tablet.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VotingGridLayoutTest {
    @Test
    void choosesResponsiveColumnsWithinTheConfiguredMaximum() {
        assertEquals(3, VotingGridLayout.columnsFor(342, 100, 6, 16, 3));
        assertEquals(2, VotingGridLayout.columnsFor(210, 100, 6, 16, 3));
        assertEquals(1, VotingGridLayout.columnsFor(90, 100, 6, 16, 3));
        assertEquals(1, VotingGridLayout.columnsFor(342, 100, 6, 1, 3));
    }

    @Test
    void computesRowsAndClampsScrollForSixteenMaps() {
        assertEquals(6, VotingGridLayout.rowCount(16, 3));
        assertEquals(0, VotingGridLayout.clampScrollRow(-5, 16, 3, 2));
        assertEquals(4, VotingGridLayout.clampScrollRow(99, 16, 3, 2));
    }

    @Test
    void movesFocusWithoutLeavingPartialGridBounds() {
        assertEquals(1, VotingGridLayout.moveIndex(0, 5, 2, VotingGridLayout.RIGHT));
        assertEquals(2, VotingGridLayout.moveIndex(0, 5, 2, VotingGridLayout.DOWN));
        assertEquals(4, VotingGridLayout.moveIndex(3, 5, 2, VotingGridLayout.DOWN));
        assertEquals(4, VotingGridLayout.moveIndex(4, 5, 2, VotingGridLayout.RIGHT));
        assertEquals(0, VotingGridLayout.moveIndex(0, 5, 2, VotingGridLayout.LEFT));
    }
}
