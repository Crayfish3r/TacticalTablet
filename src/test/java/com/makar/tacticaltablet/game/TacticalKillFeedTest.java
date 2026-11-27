package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.tablet.net.KillFeedPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.UUID;

class TacticalKillFeedTest {
    @Test void npcIdentityNeedsNoPlayerInfoAndRewardsArePersonal() {
        UUID killer = UUID.randomUUID(), npc = UUID.randomUUID();
        var reward = new TacticalKillFeed.KillReward(3, 4);
        var own = TacticalKillFeed.npcPacket(killer, "Killer", -1, npc, "Bandit",
                KillFeedPacket.Cause.NONE, "AK47", 42, killer, reward);
        var other = TacticalKillFeed.npcPacket(killer, "Killer", -1, npc, "Bandit",
                KillFeedPacket.Cause.NONE, "AK47", 42, UUID.randomUUID(), reward);
        assertEquals(npc, own.victimUuid());
        assertEquals("Bandit", own.victimName());
        assertEquals("AK47", own.weaponDisplayName());
        assertEquals(3, own.awardedCoins());
        assertEquals(4, own.awardedXp());
        assertEquals(0, other.awardedCoins());
        assertEquals(0, other.awardedXp());
        assertEquals(own.victimName(), other.victimName());
    }

    @Test void unnamedNpcAndRejectedXpHaveHonestFallbacks() {
        UUID killer = UUID.randomUUID();
        for (String name : new String[]{null, "", "  "}) {
            var packet = TacticalKillFeed.npcPacket(killer, "K", -1, UUID.randomUUID(), name,
                    KillFeedPacket.Cause.NONE, "", 1, killer, new TacticalKillFeed.KillReward(3, 0));
            assertEquals("NPC", packet.victimName());
            assertEquals(3, packet.awardedCoins());
            assertEquals(0, packet.awardedXp());
        }
    }
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
