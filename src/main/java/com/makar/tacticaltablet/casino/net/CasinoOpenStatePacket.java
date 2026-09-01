package com.makar.tacticaltablet.casino.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public final class CasinoOpenStatePacket {
    private final UUID sessionId;
    private final int balance;
    private final boolean spectatorSource;

    public CasinoOpenStatePacket(UUID sessionId, int balance, boolean spectatorSource) {
        this.sessionId = sessionId;
        this.balance = Math.max(0, balance);
        this.spectatorSource = spectatorSource;
    }

    public CasinoOpenStatePacket(FriendlyByteBuf buffer) {
        sessionId = buffer.readUUID();
        balance = Math.max(0, buffer.readVarInt());
        spectatorSource = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(sessionId);
        buffer.writeVarInt(balance);
        buffer.writeBoolean(spectatorSource);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.makar.tacticaltablet.client.casino.CasinoClientAccess.open(sessionId, balance, spectatorSource)));
        context.setPacketHandled(true);
    }
}
