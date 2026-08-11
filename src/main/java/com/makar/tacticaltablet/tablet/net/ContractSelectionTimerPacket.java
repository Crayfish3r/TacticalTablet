package com.makar.tacticaltablet.tablet.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ContractSelectionTimerPacket(
        boolean selectionActive,
        int selectionSecondsLeft
) {
    public ContractSelectionTimerPacket {
        selectionSecondsLeft = Math.max(0, selectionSecondsLeft);
    }

    public ContractSelectionTimerPacket(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(selectionActive);
        buf.writeInt(selectionSecondsLeft);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> invokeClientHandler()));
        ctx.get().setPacketHandled(true);
    }

    private void invokeClientHandler() {
        try {
            Class<?> handler = Class.forName("com.makar.tacticaltablet.tablet.client.ContractClientPacketHandler");
            handler.getMethod("handleSelectionTimer", ContractSelectionTimerPacket.class).invoke(null, this);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Failed to handle contract selection timer packet on client", exception);
        }
    }
}
