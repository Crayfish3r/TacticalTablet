package com.makar.tacticaltablet.casino.net;

import com.makar.tacticaltablet.casino.CasinoSessionManager;
import com.makar.tacticaltablet.tablet.net.PacketHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class CasinoSpectatorOpenPacket {
    public CasinoSpectatorOpenPacket() {
    }

    public CasinoSpectatorOpenPacket(FriendlyByteBuf ignored) {
    }

    public void encode(FriendlyByteBuf ignored) {
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !PacketHandler.allowC2S(player, PacketHandler.C2SAction.CASINO)) return;
            CasinoSessionManager.openFromSpectator(player);
        });
        context.setPacketHandled(true);
    }
}
