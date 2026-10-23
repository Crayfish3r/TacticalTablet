package com.makar.tacticaltablet.tablet.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.makar.tacticaltablet.tablet.client.UpgradePending.State.*;

class UpgradePendingTest {
    @Test void onlyReachedSnapshotConfirmsOnceAndAllowsNextTier() {
        UpgradePending p=new UpgradePending();
        assertTrue(p.begin("sniper",1,100));
        assertFalse(p.begin("sniper",1,101));
        assertFalse(p.begin("sniper",2,101));
        assertFalse(p.observe("scout",1,102));
        assertFalse(p.observe("sniper",0,103));
        assertEquals(WAITING,p.state("sniper",1));
        assertTrue(p.observe("sniper",1,104));
        assertEquals(CONFIRMED,p.state("sniper",1));
        assertFalse(p.observe("sniper",1,105));
        assertTrue(p.begin("sniper",2,106));
    }
    @Test void timeoutIsUncertaintyWithoutRetryAndLateSnapshotStillConfirms() {
        UpgradePending p=new UpgradePending();
        p.begin("sniper",1,100);
        p.tick(100+UpgradePending.TIMEOUT_MILLIS-1);
        assertEquals(WAITING,p.state("sniper"));
        p.tick(100+UpgradePending.TIMEOUT_MILLIS);
        assertEquals(UNCONFIRMED,p.state("sniper"));
        assertFalse(p.begin("sniper",1,20_000));
        assertFalse(p.observe("sniper",0,20_001));
        assertTrue(p.observe("sniper",1,20_002));
        assertEquals(CONFIRMED,p.state("sniper"));
    }
    @Test void independentClassesAndInvalidTargets() {
        UpgradePending p=new UpgradePending();
        assertFalse(p.begin("sniper",0,0));
        assertFalse(p.begin("sniper",99,0));
        assertTrue(p.begin("sniper",1,0));
        assertTrue(p.begin("scout",1,0));
        assertTrue(p.observe("scout",2,1));
        assertEquals(WAITING,p.state("sniper"));
        assertEquals(NONE,p.state("mortarman"));
    }
    @Test void sessionResetDoesNotCarryUncertaintyToAnotherServer() {
        UpgradePending p=new UpgradePending();
        p.begin("sniper",1,0);
        p.clear();
        assertEquals(NONE,p.state("sniper"));
        assertTrue(p.begin("sniper",1,1));
    }
}
