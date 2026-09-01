package com.makar.tacticaltablet.casino.net;

import com.makar.tacticaltablet.casino.CasinoSessionManager;
import com.makar.tacticaltablet.tablet.net.PacketHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public final class CasinoSpinRequestPacket {
    private final UUID sessionId;
    private final UUID requestId;
    private final int stake;

    public CasinoSpinRequestPacket(UUID sessionId, UUID requestId, int stake) {
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.stake = stake;
    }

    public CasinoSpinRequestPacket(FriendlyByteBuf buffer) {
        sessionId = buffer.readUUID();
        requestId = buffer.readUUID();
        stake = buffer.readVarInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(sessionId);
        buffer.writeUUID(requestId);
        buffer.writeVarInt(stake);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !PacketHandler.allowC2S(player, PacketHandler.C2SAction.CASINO)) return;
            CasinoSessionManager.spin(player, sessionId, requestId, stake);
        });
        context.setPacketHandled(true);
    }
}
