package com.makar.tacticaltablet.tablet.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class SpectatorCameraLockStatePacket {

    private final boolean locked;

    public SpectatorCameraLockStatePacket(boolean locked) {
        this.locked = locked;
    }

    public SpectatorCameraLockStatePacket(FriendlyByteBuf buf) {
        locked = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(locked);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.makar.tacticaltablet.client.SpectatorCameraClientState.setLocked(locked)
        ));
        context.setPacketHandled(true);
    }
}
