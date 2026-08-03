package com.makar.tacticaltablet.tablet.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabletPageStateTest {
    @Test
    void validatesPageNavigationWithoutDependingOnScreenLifecycle() {
        TabletPageState state = new TabletPageState();

        assertEquals(0, state.currentPage());
        assertFalse(state.selectPage(0, 5));
        assertFalse(state.selectPage(5, 5));
        assertTrue(state.selectPage(3, 5));
        assertEquals(3, state.currentPage());
    }

    @Test
    void keepsViewportOffsetsIndependentAndClamped() {
        TabletPageState state = new TabletPageState();

        assertEquals(3, state.scroll(TabletPageState.CLAN_LIST, 3, 8));
        assertEquals(1, state.scroll(TabletPageState.CLAN_MEMBERS, 1, 2));
        assertEquals(8, state.scroll(TabletPageState.CLAN_LIST, 20, 8));
        assertEquals(1, state.offset(TabletPageState.CLAN_MEMBERS));
    }

    @Test
    void changingClanResetsOnlyDetailViewports() {
        TabletPageState state = new TabletPageState();
        state.scroll(TabletPageState.CLAN_LIST, 2, 4);
        state.scroll(TabletPageState.CLAN_MEMBERS, 1, 2);
        state.scroll(TabletPageState.CLAN_PENDING, 1, 2);

        state.selectClan(2);

        assertEquals(2, state.selectedClanIndex());
        assertEquals(2, state.offset(TabletPageState.CLAN_LIST));
        assertEquals(0, state.offset(TabletPageState.CLAN_MEMBERS));
        assertEquals(0, state.offset(TabletPageState.CLAN_PENDING));
    }
}
