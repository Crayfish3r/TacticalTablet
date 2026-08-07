package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.tablet.net.KillFeedPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TacticalKillFeedTest {
    @Test
    void directBulletHasNoInventedPeriodicCause() {
        assertEquals(KillFeedPacket.Cause.NONE, TacticalKillFeed.classifyCause("tacz.bullet", false));
    }

    @Test
    void mdcMagicFallbackIsPresentedAsBleeding() {
        assertEquals(KillFeedPacket.Cause.BLEEDING, TacticalKillFeed.classifyCause("magic", true));
    }

    @Test
    void fireAfterPvpAndUnattributedFallKeepEnvironmentalCause() {
        assertEquals(KillFeedPacket.Cause.FIRE, TacticalKillFeed.classifyCause("onFire", true));
        assertEquals(KillFeedPacket.Cause.FALL, TacticalKillFeed.classifyCause("fall", false));
    }

    @Test
    void lavaAndWorldBorderHaveHonestEnvironmentalLabels() {
        assertEquals(KillFeedPacket.Cause.LAVA, TacticalKillFeed.classifyCause("lava", false));
        assertEquals(KillFeedPacket.Cause.ZONE, TacticalKillFeed.classifyCause("outsideBorder", false));
    }

    @Test
    void reliableWeaponIdGetsCompactDisplayName() {
        assertEquals("AK-74", TacticalKillFeed.weaponDisplayName("tacz:ak_74"));
        assertEquals("", TacticalKillFeed.weaponDisplayName(""));
    }
}
