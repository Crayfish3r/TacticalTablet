package com.makar.tacticaltablet.client;

import com.makar.tacticaltablet.tablet.net.KillFeedPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KillFeedClientStateTest {
    @AfterEach
    void clear() {
        KillFeedClientState.clear();
    }

    @Test
    void keepsAtMostFiveEntriesForFiveSecondsAndFades() {
        for (int i = 0; i < 6; i++) {
            KillFeedClientState.handle(new KillFeedPacket("K" + i, "V" + i,
                    KillFeedPacket.Cause.NONE, ""));
        }
        assertEquals(5, KillFeedClientState.entries().size());
        assertEquals("V5", KillFeedClientState.entries().get(0).packet().victimName());

        for (int i = 0; i < KillFeedClientState.VISIBLE_TICKS - 10; i++) KillFeedClientState.tick();
        assertTrue(KillFeedClientState.entries().get(0).alpha() < 1.0F);
        for (int i = 0; i < 10; i++) KillFeedClientState.tick();
        assertTrue(KillFeedClientState.entries().isEmpty());
    }
}
