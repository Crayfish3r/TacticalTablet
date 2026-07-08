package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.game.contract.ContractManager;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TrackerWatchPacket {

    private final boolean watching;

    public TrackerWatchPacket(boolean watching) {
        this.watching = watching;
    }

    public TrackerWatchPacket(FriendlyByteBuf buf) {
        this.watching = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(watching);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }

            if (watching) {
                ContractManager.addTrackerViewer(player);
            } else {
                ContractManager.removeTrackerViewer(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
