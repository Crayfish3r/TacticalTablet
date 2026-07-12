package com.makar.tacticaltablet.game.zone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZonePacingPolicyTest {
    private static final int PHASE_360 = 0;
    private static final int PHASE_260 = 1;
    private static final int PHASE_180 = 2;
    private static final int PHASE_110 = 3;
    private static final int PHASE_50 = 4;
    private static final int PHASE_1 = 5;
    private static final int PHASE_COUNT = 6;
    private static final int FINAL_REVEAL_START_PHASE_INDEX = PHASE_COUNT - 2;

    @Test
    void smallMatchesStartAtExisting180Phase() {
        assertEquals(PHASE_180, ZonePacingPolicy.initialPhaseIndex(1, PHASE_180));
        assertEquals(PHASE_180, ZonePacingPolicy.initialPhaseIndex(2, PHASE_180));
        assertEquals(PHASE_180, ZonePacingPolicy.initialPhaseIndex(4, PHASE_180));
    }

    @Test
    void standardMatchesKeep360Start() {
        assertEquals(PHASE_360, ZonePacingPolicy.initialPhaseIndex(5, PHASE_180));
        assertEquals(PHASE_360, ZonePacingPolicy.initialPhaseIndex(8, PHASE_180));
        assertEquals(PHASE_360, ZonePacingPolicy.initialPhaseIndex(9, PHASE_180));
    }

    @Test
    void smallMatchSkips360And260ThenContinuesTo110() {
        int initial = ZonePacingPolicy.initialPhaseIndex(4, PHASE_180);

        assertEquals(PHASE_180, initial);
        assertEquals(PHASE_110, initial + 1);
    }

    @Test
    void standardPhaseSequenceIsUnchanged() {
        assertEquals(PHASE_360, ZonePacingPolicy.initialPhaseIndex(5, PHASE_180));
        assertEquals(PHASE_260, PHASE_360 + 1);
        assertEquals(PHASE_180, PHASE_260 + 1);
        assertEquals(PHASE_110, PHASE_180 + 1);
        assertEquals(PHASE_50, PHASE_110 + 1);
        assertEquals(PHASE_1, PHASE_50 + 1);
    }

    @Test
    void finalRevealStartsAt50AndContinuesAt1() {
        assertFalse(ZonePacingPolicy.finalRevealEnabled(PHASE_360, FINAL_REVEAL_START_PHASE_INDEX, PHASE_COUNT));
        assertFalse(ZonePacingPolicy.finalRevealEnabled(PHASE_260, FINAL_REVEAL_START_PHASE_INDEX, PHASE_COUNT));
        assertFalse(ZonePacingPolicy.finalRevealEnabled(PHASE_180, FINAL_REVEAL_START_PHASE_INDEX, PHASE_COUNT));
        assertFalse(ZonePacingPolicy.finalRevealEnabled(PHASE_110, FINAL_REVEAL_START_PHASE_INDEX, PHASE_COUNT));
        assertTrue(ZonePacingPolicy.finalRevealEnabled(PHASE_50, FINAL_REVEAL_START_PHASE_INDEX, PHASE_COUNT));
        assertTrue(ZonePacingPolicy.finalRevealEnabled(PHASE_1, FINAL_REVEAL_START_PHASE_INDEX, PHASE_COUNT));
    }

    @Test
    void finalRevealCadencePulsesEvery30Seconds() {
        ZoneFinalRevealScheduler scheduler =
                new ZoneFinalRevealScheduler(ZonePacingPolicy.FINAL_REVEAL_INTERVAL_SECONDS);

        for (int second = 1; second <= 29; second++) {
            assertFalse(scheduler.tick(PHASE_50, FINAL_REVEAL_START_PHASE_INDEX, PHASE_COUNT), "second " + second);
        }
        assertTrue(scheduler.tick(PHASE_50, FINAL_REVEAL_START_PHASE_INDEX, PHASE_COUNT));

        for (int second = 1; second <= 29; second++) {
            assertFalse(scheduler.tick(PHASE_50, FINAL_REVEAL_START_PHASE_INDEX, PHASE_COUNT), "repeat second " + second);
        }
        assertTrue(scheduler.tick(PHASE_50, FINAL_REVEAL_START_PHASE_INDEX, PHASE_COUNT));
    }

    @Test
    void finalRevealContinuesInInfiniteLastPhase() {
        ZoneFinalRevealScheduler scheduler =
                new ZoneFinalRevealScheduler(ZonePacingPolicy.FINAL_REVEAL_INTERVAL_SECONDS);

        for (int second = 1; second <= 29; second++) {
            assertFalse(scheduler.tick(PHASE_1, FINAL_REVEAL_START_PHASE_INDEX, PHASE_COUNT), "second " + second);
        }

        assertTrue(scheduler.tick(PHASE_1, FINAL_REVEAL_START_PHASE_INDEX, PHASE_COUNT));
    }

    @Test
    void transitionFrom50To1DoesNotResetCadence() {
        ZoneFinalRevealScheduler scheduler =
                new ZoneFinalRevealScheduler(ZonePacingPolicy.FINAL_REVEAL_INTERVAL_SECONDS);

        for (int second = 1; second <= 10; second++) {
            assertFalse(scheduler.tick(PHASE_50, FINAL_REVEAL_START_PHASE_INDEX, PHASE_COUNT), "phase 50 second " + second);
        }
        for (int second = 11; second <= 29; second++) {
            assertFalse(scheduler.tick(PHASE_1, FINAL_REVEAL_START_PHASE_INDEX, PHASE_COUNT), "phase 1 second " + second);
        }

        assertTrue(scheduler.tick(PHASE_1, FINAL_REVEAL_START_PHASE_INDEX, PHASE_COUNT));
    }

    @Test
    void resetClearsRevealTimerForNextMatch() {
        ZoneFinalRevealScheduler scheduler =
                new ZoneFinalRevealScheduler(ZonePacingPolicy.FINAL_REVEAL_INTERVAL_SECONDS);

        for (int second = 1; second <= 29; second++) {
            assertFalse(scheduler.tick(PHASE_50, FINAL_REVEAL_START_PHASE_INDEX, PHASE_COUNT), "before reset " + second);
        }
        scheduler.reset();

        for (int second = 1; second <= 29; second++) {
            assertFalse(scheduler.tick(PHASE_50, FINAL_REVEAL_START_PHASE_INDEX, PHASE_COUNT), "after reset " + second);
        }
        assertTrue(scheduler.tick(PHASE_50, FINAL_REVEAL_START_PHASE_INDEX, PHASE_COUNT));
    }

    @Test
    void leavingFinalPhasesClearsRevealScheduler() {
        ZoneFinalRevealScheduler scheduler =
                new ZoneFinalRevealScheduler(ZonePacingPolicy.FINAL_REVEAL_INTERVAL_SECONDS);

        assertFalse(scheduler.tick(PHASE_50, FINAL_REVEAL_START_PHASE_INDEX, PHASE_COUNT));
        assertTrue(scheduler.active());

        assertFalse(scheduler.tick(PHASE_110, FINAL_REVEAL_START_PHASE_INDEX, PHASE_COUNT));
        assertFalse(scheduler.active());
        assertEquals(ZonePacingPolicy.FINAL_REVEAL_INTERVAL_SECONDS, scheduler.secondsUntilNext());
    }

    @Test
    void revealTargetsOnlyLivingPlayingNonSpectators() {
        assertTrue(ZonePacingPolicy.isFinalRevealTarget(true, false, true));
        assertFalse(ZonePacingPolicy.isFinalRevealTarget(false, false, true));
        assertFalse(ZonePacingPolicy.isFinalRevealTarget(true, true, true));
        assertFalse(ZonePacingPolicy.isFinalRevealTarget(true, false, false));
    }

    @Test
    void revealDurationIsFiveSecondsInTicks() {
        assertEquals(100, ZonePacingPolicy.FINAL_REVEAL_DURATION_TICKS);
    }
}
