package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetReportDispatchFlagTest {
    @Test
    void successfulSaveKeepsDispatchedFlag() {
        AtomicBoolean flag = new AtomicBoolean();

        assertTrue(SetReportDispatchFlag.persistTrue(false, flag::set, () -> true));
        assertTrue(flag.get());
    }

    @Test
    void failedSaveRollsFlagBackToPending() {
        AtomicBoolean flag = new AtomicBoolean();

        assertFalse(SetReportDispatchFlag.persistTrue(false, flag::set, () -> false));
        assertFalse(flag.get());
    }
}
