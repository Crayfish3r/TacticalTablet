package com.makar.tacticaltablet.airdrop.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class AirdropNoticePacket {
    private static final int MAX_MESSAGE_LENGTH = 64;
    private static final int MIN_DURATION_TICKS = 20;
    private static final int MAX_DURATION_TICKS = 200;

    private final String message;
    private final int color;
    private final int durationTicks;
    private final NoticeType type;

    public AirdropNoticePacket(String message, int color, int durationTicks, NoticeType type) {
        this.message = sanitizeMessage(message);
        this.color = color;
        this.durationTicks = clampDuration(durationTicks);
        this.type = type == null ? NoticeType.COUNTDOWN_60 : type;
    }

    public AirdropNoticePacket(FriendlyByteBuf buf) {
        this.message = sanitizeMessage(buf.readUtf(MAX_MESSAGE_LENGTH));
        this.color = buf.readInt();
        this.durationTicks = clampDuration(buf.readVarInt());
        this.type = NoticeType.byId(buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(message, MAX_MESSAGE_LENGTH);
        buf.writeInt(color);
        buf.writeVarInt(durationTicks);
        buf.writeVarInt(type.id());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::invokeClientHandler));
        context.setPacketHandled(true);
    }

    private void invokeClientHandler() {
        try {
            Class<?> handler = Class.forName("com.makar.tacticaltablet.airdrop.client.AirdropNoticeClientState");
            handler.getMethod("handle", AirdropNoticePacket.class).invoke(null, this);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Failed to show AirDrop notice on client", exception);
        }
    }

    public String message() {
        return message;
    }

    public int color() {
        return color;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public NoticeType type() {
        return type;
    }

    private static String sanitizeMessage(String value) {
        if (value == null) return "";
        return value.length() > MAX_MESSAGE_LENGTH ? value.substring(0, MAX_MESSAGE_LENGTH) : value;
    }

    private static int clampDuration(int durationTicks) {
        return Math.max(MIN_DURATION_TICKS, Math.min(MAX_DURATION_TICKS, durationTicks));
    }

    public enum NoticeType {
        COUNTDOWN_60(0),
        COUNTDOWN_30(1),
        DROPPING(2);

        private final int id;

        NoticeType(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static NoticeType byId(int id) {
            for (NoticeType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return COUNTDOWN_60;
        }
    }
}
