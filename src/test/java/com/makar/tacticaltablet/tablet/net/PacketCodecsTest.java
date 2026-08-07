package com.makar.tacticaltablet.tablet.net;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PacketCodecsTest {

    private enum Sample { FIRST, SECOND }

    @Test
    void rejectsNegativeAndOversizedCollectionSizesBeforeAllocation() {
        assertThrows(IllegalArgumentException.class, () -> PacketCodecs.requireSize(-1, 4, "items"));
        assertThrows(IllegalArgumentException.class, () -> PacketCodecs.requireSize(5, 4, "items"));
        assertEquals(4, PacketCodecs.requireSize(4, 4, "items"));
    }

    @Test
    void validatesEnumOrdinals() {
        FriendlyByteBuf valid = new FriendlyByteBuf(Unpooled.buffer());
        valid.writeByte(1);
        assertEquals(Sample.SECOND, PacketCodecs.readEnumOrdinal(valid, Sample.values(), "sample"));

        FriendlyByteBuf invalid = new FriendlyByteBuf(Unpooled.buffer());
        invalid.writeByte(99);
        assertThrows(IllegalArgumentException.class,
                () -> PacketCodecs.readEnumOrdinal(invalid, Sample.values(), "sample"));
    }

    @Test
    void enforcesUtfLengthAtDecodeBoundary() {
        FriendlyByteBuf accepted = new FriendlyByteBuf(Unpooled.buffer());
        accepted.writeUtf("abcd", 4);
        assertEquals("abcd", accepted.readUtf(4));

        FriendlyByteBuf rejected = new FriendlyByteBuf(Unpooled.buffer());
        rejected.writeUtf("abcde", 5);
        assertThrows(RuntimeException.class, () -> rejected.readUtf(4));
    }

    @Test
    void killFeedPacketRoundTripsBoundedServerData() {
        KillFeedPacket original = new KillFeedPacket(UUID.randomUUID(), "Killer", 0xFFFF5555,
                UUID.randomUUID(), "Victim", KillFeedPacket.NO_TEAM_COLOR,
                KillFeedPacket.Cause.BLEEDING, "AK-47", 1234L, 5, 12);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        original.encode(buf);
        assertEquals(original, new KillFeedPacket(buf));
    }

    @Test
    void killFeedBoundsLongNamesAndWeaponLabels() {
        KillFeedPacket packet = new KillFeedPacket(null, "K".repeat(80), KillFeedPacket.NO_TEAM_COLOR,
                UUID.randomUUID(), "V".repeat(80), KillFeedPacket.NO_TEAM_COLOR,
                KillFeedPacket.Cause.NONE, "W".repeat(100), 1L, 0, 0);
        assertEquals(32, packet.killerName().length());
        assertEquals(32, packet.victimName().length());
        assertEquals(48, packet.weaponDisplayName().length());
    }
}
