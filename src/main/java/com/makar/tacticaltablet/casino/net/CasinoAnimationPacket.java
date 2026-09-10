package com.makar.tacticaltablet.casino.net;

import com.makar.tacticaltablet.casino.CasinoReelResult;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

/** Additional S2C timeline; old result packet 39 retains its original layout. */
public record CasinoAnimationPacket(UUID sessionId, UUID requestId, long startTick, int duration,
                                    long seed, CasinoReelResult previous, CasinoReelResult target) {
    public CasinoAnimationPacket {
        if (duration < 0 || duration > 80) throw new IllegalArgumentException("Invalid casino duration");
    }
    public CasinoAnimationPacket(FriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readUUID(), buf.readLong(), buf.readVarInt(), buf.readLong(), read(buf), read(buf));
    }
    private static CasinoReelResult read(FriendlyByteBuf buf) {
        return new CasinoReelResult(buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedByte());
    }
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(sessionId); buf.writeUUID(requestId); buf.writeLong(startTick);
        buf.writeVarInt(duration); buf.writeLong(seed);
        for (int i = 0; i < 3; i++) buf.writeByte(previous.symbol(i));
        for (int i = 0; i < 3; i++) buf.writeByte(target.symbol(i));
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.makar.tacticaltablet.client.casino.CasinoClientAccess.animation(this)));
        context.setPacketHandled(true);
    }
}
