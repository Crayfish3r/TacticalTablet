package com.makar.tacticaltablet.casino.net;

import com.makar.tacticaltablet.casino.CasinoRewardKind;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public final class CasinoSpinResultPacket {
    private static final int MAX_CLASS_ID_LENGTH = 64;

    public enum Status {
        SUCCESS,
        BUSY,
        INSUFFICIENT_COINS,
        INVALID_SESSION,
        INVALID_REQUEST,
        SAVE_FAILED,
        CASINO_MACHINE_BUSY
    }

    private final UUID sessionId;
    private final UUID requestId;
    private final Status status;
    private final int balance;
    private final CasinoRewardKind rewardKind;
    private final int awardedCoins;
    private final String classId;
    private final boolean duplicate;
    private final int animationTicks;
    private final long animationSeed;

    public CasinoSpinResultPacket(
            UUID sessionId,
            UUID requestId,
            Status status,
            int balance,
            CasinoRewardKind rewardKind,
            int awardedCoins,
            String classId,
            boolean duplicate,
            int animationTicks,
            long animationSeed
    ) {
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.status = status;
        this.balance = Math.max(0, balance);
        this.rewardKind = rewardKind;
        this.awardedCoins = Math.max(0, awardedCoins);
        this.classId = sanitize(classId);
        this.duplicate = duplicate;
        this.animationTicks = Math.max(0, animationTicks);
        this.animationSeed = animationSeed;
    }

    public CasinoSpinResultPacket(FriendlyByteBuf buffer) {
        sessionId = buffer.readUUID();
        requestId = buffer.readUUID();
        status = Status.values()[buffer.readVarInt()];
        balance = Math.max(0, buffer.readVarInt());
        rewardKind = CasinoRewardKind.values()[buffer.readVarInt()];
        awardedCoins = Math.max(0, buffer.readVarInt());
        classId = buffer.readUtf(MAX_CLASS_ID_LENGTH);
        duplicate = buffer.readBoolean();
        animationTicks = Math.max(0, buffer.readVarInt());
        animationSeed = buffer.readLong();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(sessionId);
        buffer.writeUUID(requestId);
        buffer.writeVarInt(status.ordinal());
        buffer.writeVarInt(balance);
        buffer.writeVarInt(rewardKind.ordinal());
        buffer.writeVarInt(awardedCoins);
        buffer.writeUtf(classId, MAX_CLASS_ID_LENGTH);
        buffer.writeBoolean(duplicate);
        buffer.writeVarInt(animationTicks);
        buffer.writeLong(animationSeed);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.makar.tacticaltablet.client.casino.CasinoClientAccess.result(
                        sessionId,
                        requestId,
                        status,
                        balance,
                        rewardKind,
                        awardedCoins,
                        classId,
                        duplicate,
                        animationTicks,
                        animationSeed
                )));
        context.setPacketHandled(true);
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        return value.length() <= MAX_CLASS_ID_LENGTH ? value : value.substring(0, MAX_CLASS_ID_LENGTH);
    }
}
