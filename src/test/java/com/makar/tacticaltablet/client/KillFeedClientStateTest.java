package com.makar.tacticaltablet.client;

import com.makar.tacticaltablet.tablet.net.KillFeedPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

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
            KillFeedClientState.handle(packet(UUID.randomUUID(), "K" + i, "V" + i, i, 0, 0));
        }
        assertEquals(5, KillFeedClientState.entries().size());
        assertEquals("V5", KillFeedClientState.entries().get(0).packet().victimName());

        for (int i = 0; i < KillFeedClientState.VISIBLE_TICKS - 10; i++) KillFeedClientState.tick();
        assertTrue(KillFeedClientState.entries().get(0).alpha() < 1.0F);
        for (int i = 0; i < 10; i++) KillFeedClientState.tick();
        assertTrue(KillFeedClientState.entries().isEmpty());
    }

    @Test
    void duplicateDeathPacketIsRenderedOnlyOnce() {
        UUID victim = UUID.randomUUID();
        KillFeedPacket packet = packet(victim, "K", "V", 42, 5, 12);
        KillFeedClientState.handle(packet);
        KillFeedClientState.handle(packet);
        assertEquals(1, KillFeedClientState.entries().size());
    }

    @Test
    void rewardLineOmitsZeroValues() {
        assertEquals("+5 coins   +12 XP", KillFeedOverlay.rewardText(
                packet(UUID.randomUUID(), "K", "V", 1, 5, 12)));
        assertEquals("+5 coins", KillFeedOverlay.rewardText(
                packet(UUID.randomUUID(), "K", "V", 2, 5, 0)));
        assertEquals("+12 XP", KillFeedOverlay.rewardText(
                packet(UUID.randomUUID(), "K", "V", 3, 0, 12)));
        assertEquals("+8 coins", KillFeedOverlay.rewardText(
                packet(UUID.randomUUID(), "K", "V", 4, 8, 0)));
    }

    private static KillFeedPacket packet(UUID victim, String killer, String victimName, long time,
                                         int coins, int xp) {
        return new KillFeedPacket(UUID.randomUUID(), killer, 0xFFFF5555, victim, victimName,
                KillFeedPacket.NO_TEAM_COLOR, KillFeedPacket.Cause.NONE, "", time, coins, xp);
    }
}
