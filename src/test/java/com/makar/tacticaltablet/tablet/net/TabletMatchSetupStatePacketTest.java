package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.game.MatchMode;
import com.makar.tacticaltablet.game.MatchPhase;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabletMatchSetupStatePacketTest {

    @Test
    void roundTripsMatchSetupStateWithNullableVote() {
        TabletMatchSetupStatePacket original = new TabletMatchSetupStatePacket(
                MatchPhase.TEAM_SELECT,
                MatchMode.TRIO,
                null,
                7,
                1,
                2,
                3,
                4,
                15,
                11,
                3,
                2,
                Map.of("2:0", "Player"),
                true,
                false
        );
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        original.encode(buf);
        TabletMatchSetupStatePacket decoded = new TabletMatchSetupStatePacket(buf);

        assertEquals(original, decoded);
        assertNull(decoded.selectedVote());
    }

    @Test
    void boundsTeamSlotCountKeysAndPlayerNamesBeforeEncoding() {
        Map<String, String> slots = new LinkedHashMap<>();
        for (int i = 0; i < 40; i++) {
            slots.put(String.format("%02d:oversized-key", i), "Player-" + i + "x".repeat(40));
        }

        TabletMatchSetupStatePacket packet = packetWith(slots);

        assertEquals(TabletMatchSetupStatePacket.MAX_TEAM_SLOT_ENTRIES, packet.teamSlots().size());
        assertTrue(packet.teamSlots().keySet().stream()
                .allMatch(key -> key.length() <= TabletMatchSetupStatePacket.MAX_TEAM_SLOT_KEY_LENGTH));
        assertTrue(packet.teamSlots().values().stream()
                .allMatch(name -> name.length() <= TabletMatchSetupStatePacket.MAX_PLAYER_NAME_LENGTH));
    }

    @Test
    void rejectsOversizedTeamSlotPayloadBeforeAllocation() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        writeHeader(buf);
        buf.writeInt(TabletMatchSetupStatePacket.MAX_TEAM_SLOT_ENTRIES + 1);

        assertThrows(IllegalArgumentException.class, () -> new TabletMatchSetupStatePacket(buf));
    }

    @Test
    void rejectsInvalidEnumOrdinalsThroughSafeCodec() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(99);

        assertThrows(IllegalArgumentException.class, () -> new TabletMatchSetupStatePacket(buf));
    }

    private static TabletMatchSetupStatePacket packetWith(Map<String, String> teamSlots) {
        return new TabletMatchSetupStatePacket(
                MatchPhase.VOTING,
                MatchMode.SOLO,
                MatchMode.DUO,
                1,
                0,
                0,
                0,
                0,
                15,
                0,
                1,
                -1,
                teamSlots,
                false,
                false
        );
    }

    private static void writeHeader(FriendlyByteBuf buf) {
        buf.writeByte(MatchPhase.VOTING.ordinal());
        buf.writeByte(MatchMode.SOLO.ordinal());
        buf.writeByte(-1);
        for (int i = 0; i < 9; i++) {
            buf.writeInt(0);
        }
    }
}
