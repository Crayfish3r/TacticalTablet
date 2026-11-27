package com.makar.tacticaltablet.tablet.client.prototype;

import org.junit.jupiter.api.Test;
import static com.makar.tacticaltablet.tablet.client.prototype.TabletAnimationController.State.*;
import static org.junit.jupiter.api.Assertions.*;

class TabletAnimationControllerTest {
    private final TabletAnimationController motion = new TabletAnimationController(0.65, 0.45);
    private static long at(double seconds) { return Math.round(seconds * 1_000_000_000L); }

    @Test void heldUseDoesNotRestartAndOpenHoldsLastPose() {
        motion.pressUse(at(0));
        motion.pressUse(at(0.3));
        assertEquals(DRAWING, motion.state());
        assertEquals(0.3, motion.seconds(at(0.3)), 1e-8);
        motion.pressUse(at(0.65));
        motion.update(at(30));
        assertEquals(OPEN, motion.state());
        assertEquals(0.65, motion.seconds(at(30)), 1e-8);
        motion.releaseUse();
        motion.pressUse(at(30));
        assertEquals(HOLSTERING, motion.state());
        motion.update(at(30.449));
        assertTrue(motion.active());
        motion.update(at(30.45));
        assertEquals(CLOSED, motion.state());
        motion.pressUse(at(31));
        assertEquals(CLOSED, motion.state());
    }

    @Test void escapeDuringDrawQueuesSmoothHolsterAndLargeTimeStepsFinish() {
        motion.pressUse(at(0));
        motion.requestClose(at(0.2));
        motion.requestClose(at(0.4));
        assertEquals(DRAWING, motion.state());
        motion.update(at(0.8));
        assertEquals(HOLSTERING, motion.state());
        assertEquals(0.15, motion.seconds(at(0.8)), 1e-8);
        motion.update(at(10));
        assertFalse(motion.active());
    }

    @Test void transitionPressesNeverDuplicateOrQueueReopen() {
        motion.pressUse(at(0));
        motion.releaseUse();
        motion.pressUse(at(0.2));
        motion.update(at(0.65));
        assertEquals(OPEN, motion.state());
        motion.requestClose(at(1));
        motion.releaseUse();
        motion.pressUse(at(1.1));
        motion.update(at(1.45));
        assertEquals(CLOSED, motion.state());
        motion.releaseUse();
        motion.pressUse(at(2));
        assertEquals(DRAWING, motion.state());
    }

    @Test void emergencyResetClearsPendingCloseButRequiresRelease() {
        motion.pressUse(at(0));
        motion.requestClose(at(0.2));
        motion.reset();
        motion.pressUse(at(2));
        assertEquals(CLOSED, motion.state());
        motion.releaseUse();
        motion.pressUse(at(3));
        motion.update(at(4));
        assertEquals(OPEN, motion.state());
    }
}
