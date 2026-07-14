package com.makar.tacticaltablet.tablet.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabletPacketUpgradeActionTest {

    @Test
    void encodesAndDecodesEveryUpgradeTierUnambiguously() {
        assertAction(200, 1);
        assertAction(300, 2);
        assertAction(400, 3);
        assertAction(500, 4);
    }

    @Test
    void rejectsInvalidAndOutOfRangeUpgradeActions() {
        assertTrue(TabletPacket.decodeUpgradeAction(-1).isEmpty());
        assertTrue(TabletPacket.decodeUpgradeAction(199).isEmpty());
        assertTrue(TabletPacket.decodeUpgradeAction(524).isEmpty());
        assertTrue(TabletPacket.decodeUpgradeAction(207).isEmpty());
        assertEquals(-1, TabletPacket.upgradeActionId(99, 1));
        assertEquals(-1, TabletPacket.upgradeActionId(0, 0));
        assertEquals(-1, TabletPacket.upgradeActionId(0, 5));
    }

    private static void assertAction(int expectedActionId, int targetTier) {
        assertEquals(expectedActionId, TabletPacket.upgradeActionId(0, targetTier));
        TabletPacket.UpgradeAction action = TabletPacket.decodeUpgradeAction(expectedActionId).orElseThrow();
        assertEquals(0, action.classActionId());
        assertEquals(targetTier, action.targetTier());
    }
}
