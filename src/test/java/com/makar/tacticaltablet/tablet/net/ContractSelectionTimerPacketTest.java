package com.makar.tacticaltablet.tablet.net;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractSelectionTimerPacketTest {

    @Test
    void roundTripsActiveTimerState() {
        ContractSelectionTimerPacket original = new ContractSelectionTimerPacket(true, 37);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        original.encode(buf);

        ContractSelectionTimerPacket decoded = new ContractSelectionTimerPacket(buf);
        assertTrue(decoded.selectionActive());
        assertEquals(37, decoded.selectionSecondsLeft());
        assertEquals(original, decoded);
    }

    @Test
    void clampsNegativeSecondsForInactiveState() {
        ContractSelectionTimerPacket packet = new ContractSelectionTimerPacket(false, -10);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.encode(buf);
        ContractSelectionTimerPacket decoded = new ContractSelectionTimerPacket(buf);

        assertFalse(decoded.selectionActive());
        assertEquals(0, decoded.selectionSecondsLeft());
    }
}
