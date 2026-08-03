package com.makar.tacticaltablet.tablet.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiFrameClockTest {
    @Test
    void producesClampedFrameIndependentDeltas() {
        UiFrameClock clock = new UiFrameClock();

        assertEquals(0.0F, clock.nextFrame(1_000L, false).deltaSeconds());
        assertEquals(1.0F / 60.0F, clock.nextFrame(1_017L, false).deltaSeconds(), 0.001F);
        assertEquals(UiFrameContext.MAX_DELTA_SECONDS,
                clock.nextFrame(10_000L, false).deltaSeconds());
        assertEquals(0.0F, clock.nextFrame(9_000L, false).deltaSeconds());
    }

    @Test
    void resetMakesTheNextFrameInitialAgain() {
        UiFrameClock clock = new UiFrameClock();
        clock.nextFrame(1_000L, false);
        clock.nextFrame(1_016L, false);

        clock.reset();

        assertEquals(0.0F, clock.nextFrame(2_000L, false).deltaSeconds());
    }

    @Test
    void nestedRenderersReuseTheTopLevelContext() {
        UiFrameContext outer = new UiFrameContext(0.016F, false);
        UiFrameContext nestedRequest = new UiFrameContext(0.033F, true);

        try (TacticalUi.FrameScope ignoredOuter = TacticalUi.openFrame(outer)) {
            assertSame(outer, TacticalUi.currentFrame());
            try (TacticalUi.FrameScope ignoredNested = TacticalUi.openFrame(nestedRequest)) {
                assertSame(outer, TacticalUi.currentFrame());
            }
            assertSame(outer, TacticalUi.currentFrame());
        }

        assertSame(UiFrameContext.INITIAL, TacticalUi.currentFrame());
        assertTrue(nestedRequest.reducedMotion());
    }

    @Test
    void selectedAndFocusedRemainIndependentVisualStates() {
        TacticalUi.ControlVisualState state =
                new TacticalUi.ControlVisualState(true, false, true, false, true);

        assertTrue(state.focused());
        assertTrue(state.selected());
        assertTrue(state.emphasized());
        assertFalse(state.pressed());
    }
}
