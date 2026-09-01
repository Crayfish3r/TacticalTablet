package com.makar.tacticaltablet.casino.net;

import com.makar.tacticaltablet.casino.CasinoSessionManager;
import com.makar.tacticaltablet.tablet.net.PacketHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public final class CasinoClosePacket {
    private final UUID sessionId;

    public CasinoClosePacket(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public CasinoClosePacket(FriendlyByteBuf buffer) {
        sessionId = buffer.readUUID();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(sessionId);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !PacketHandler.allowC2S(player, PacketHandler.C2SAction.CASINO)) return;
            CasinoSessionManager.close(player, sessionId);
        });
        context.setPacketHandled(true);
    }
}
