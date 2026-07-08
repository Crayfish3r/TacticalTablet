package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.game.SpectatorCameraManager;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SpectatorCameraSwitchPacket {

    private final int direction;

    public SpectatorCameraSwitchPacket(int direction) {
        this.direction = direction;
    }

    public SpectatorCameraSwitchPacket(FriendlyByteBuf buf) {
        this.direction = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(direction);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            int normalizedDirection = Integer.signum(direction);
            if (normalizedDirection == 0) {
                return;
            }

            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                SpectatorCameraManager.switchCamera(player, normalizedDirection);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
