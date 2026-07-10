package com.makar.tacticaltablet.tablet.net;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

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
}
