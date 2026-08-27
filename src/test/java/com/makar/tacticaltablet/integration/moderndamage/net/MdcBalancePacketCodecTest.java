package com.makar.tacticaltablet.integration.moderndamage.net;

import com.makar.tacticaltablet.integration.moderndamage.ModernDamageBalanceSchema;
import com.makar.tacticaltablet.integration.moderndamage.ModernDamageIntegration;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MdcBalancePacketCodecTest {
    @Test
    void updateRoundTripsOnlyTypedAllowListedFields() {
        Map<Integer, Double> values = ModernDamageBalanceSchema.toMap(ModernDamageBalanceSchema.defaults());
        MdcBalanceUpdatePacket original = new MdcBalanceUpdatePacket(17L, values);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        MdcBalanceUpdatePacket.encode(original, buffer);

        MdcBalanceUpdatePacket decoded = new MdcBalanceUpdatePacket(buffer);
        assertEquals(17L, decoded.expectedRevision());
        assertEquals(ModernDamageBalanceSchema.fields().size(), decoded.fields().size());
        assertEquals(values.get(0), decoded.fields().get(0).value());
    }

    @Test
    void updateDecoderRejectsOversizedForgedFieldList() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeLong(1L);
        buffer.writeVarInt(129);

        assertThrows(IllegalArgumentException.class, () -> new MdcBalanceUpdatePacket(buffer));
    }

    @Test
    void stateRoundTripsAndRejectsOversizedValueList() {
        double[] values = ModernDamageBalanceSchema.defaults();
        MdcBalanceStatePacket original = new MdcBalanceStatePacket(
                ModernDamageIntegration.State.SUPPORTED, "1.0.32", "verified", true,
                9L, values, MdcBalanceStatePacket.Result.SUCCESS);
        FriendlyByteBuf valid = new FriendlyByteBuf(Unpooled.buffer());
        MdcBalanceStatePacket.encode(original, valid);
        MdcBalanceStatePacket decoded = new MdcBalanceStatePacket(valid);
        assertEquals(ModernDamageIntegration.State.SUPPORTED, decoded.integrationState());
        assertEquals(9L, decoded.revision());
        assertArrayEquals(values, decoded.values());

        FriendlyByteBuf forged = new FriendlyByteBuf(Unpooled.buffer());
        forged.writeVarInt(ModernDamageIntegration.State.SUPPORTED.ordinal());
        forged.writeUtf("1.0.32", 32);
        forged.writeUtf("verified", 256);
        forged.writeBoolean(true);
        forged.writeLong(9L);
        forged.writeVarInt(129);
        assertThrows(IllegalArgumentException.class, () -> new MdcBalanceStatePacket(forged));
    }
}
