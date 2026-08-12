package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.game.SpectatorHudSnapshot;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectatorHudStatePacketTest {
    @Test
    void roundTripsBoundedSnapshot() {
        SpectatorHudStatePacket original = new SpectatorHudStatePacket(
                new SpectatorHudSnapshot("Player", "Снайпер", 10, 4, 2, 12));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        original.encode(buf);

        SpectatorHudSnapshot decoded = new SpectatorHudStatePacket(buf).snapshot().orElseThrow();
        assertEquals("Player", decoded.playerName());
        assertEquals("Снайпер", decoded.className());
        assertEquals(10, decoded.kills());
        assertEquals(4, decoded.deaths());
    }

    @Test
    void roundTripsExplicitClear() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        SpectatorHudStatePacket.clear().encode(buf);
        assertTrue(new SpectatorHudStatePacket(buf).snapshot().isEmpty());
    }

    @Test
    void snapshotTruncatesStringsToPacketLimits() {
        SpectatorHudStatePacket original = new SpectatorHudStatePacket(
                new SpectatorHudSnapshot("P".repeat(100), "C".repeat(100), 1, 2, 3, 4));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        original.encode(buf);

        SpectatorHudSnapshot decoded = new SpectatorHudStatePacket(buf).snapshot().orElseThrow();
        assertEquals(SpectatorHudSnapshot.MAX_PLAYER_NAME_LENGTH, decoded.playerName().length());
        assertEquals(SpectatorHudSnapshot.MAX_CLASS_NAME_LENGTH, decoded.className().length());
    }

    @Test
    void decoderRejectsPlayerNameOverLimit() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(true);
        buf.writeUtf("P".repeat(SpectatorHudSnapshot.MAX_PLAYER_NAME_LENGTH + 1), 100);
        buf.writeUtf("Class", SpectatorHudSnapshot.MAX_CLASS_NAME_LENGTH);
        buf.writeInt(0);
        buf.writeInt(0);
        buf.writeInt(0);
        buf.writeInt(0);

        assertThrows(RuntimeException.class, () -> new SpectatorHudStatePacket(buf));
    }
}
