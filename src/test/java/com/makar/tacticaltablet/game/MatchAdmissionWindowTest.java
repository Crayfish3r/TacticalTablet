package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchAdmissionWindowTest {
    private static final UUID FIRST_MATCH =
            UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_MATCH =
            UUID.fromString("70000000-0000-0000-0000-000000000002");

    @Test
    void defaultWindowIsOpenForExactlyFirstTenMinutes() {
        MatchAdmissionWindow window = new MatchAdmissionWindow();
        long startTick = 4_000L;
        long cutoffTicks = 600L * 20L;

        assertTrue(window.open(FIRST_MATCH, startTick));
        assertEquals(startTick, window.snapshot().startedAtTick());
        assertEquals(startTick + cutoffTicks, window.snapshot().deadlineTick());

        window.advance(startTick + cutoffTicks - 1L);
        assertTrue(window.snapshot().open());

        window.advance(startTick + cutoffTicks);
        assertFalse(window.snapshot().open());
    }

    @Test
    void duplicateStartForSameMatchCannotExtendDeadline() {
        MatchAdmissionWindow window = new MatchAdmissionWindow();

        assertTrue(window.open(FIRST_MATCH, 100L));
        long originalDeadline = window.snapshot().deadlineTick();
        window.advance(5_000L);

        assertFalse(window.open(FIRST_MATCH, 5_000L));
        assertEquals(originalDeadline, window.snapshot().deadlineTick());
        assertEquals(5_000L, window.snapshot().currentTick());
    }

    @Test
    void eachNewMatchGetsAFreshWindow() {
        MatchAdmissionWindow window = new MatchAdmissionWindow();
        window.open(FIRST_MATCH, 100L);
        long firstDeadline = window.snapshot().deadlineTick();

        assertTrue(window.open(SECOND_MATCH, 50_000L));
        assertEquals(SECOND_MATCH, window.snapshot().matchId());
        assertEquals(50_000L, window.snapshot().startedAtTick());
        assertTrue(window.snapshot().deadlineTick() > firstDeadline);
        assertTrue(window.snapshot().open());
    }

    @Test
    void staleCleanupCannotClearNewMatchButFullResetCan() {
        MatchAdmissionWindow window = new MatchAdmissionWindow();
        window.open(SECOND_MATCH, 50_000L);

        assertFalse(window.clear(FIRST_MATCH));
        assertEquals(SECOND_MATCH, window.snapshot().matchId());

        assertTrue(window.clear(SECOND_MATCH));
        assertNull(window.snapshot().matchId());
        assertFalse(window.snapshot().open());

        window.open(FIRST_MATCH, 100L);
        assertTrue(window.clear(null));
        assertNull(window.snapshot().matchId());
    }
}
