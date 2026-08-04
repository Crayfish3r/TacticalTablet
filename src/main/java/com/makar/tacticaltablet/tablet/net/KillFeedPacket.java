package com.makar.tacticaltablet.tablet.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record KillFeedPacket(String killerName, String victimName, Cause cause, String weaponId) {
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_WEAPON_ID_LENGTH = 96;

    public KillFeedPacket {
        killerName = sanitize(killerName, MAX_NAME_LENGTH);
        victimName = sanitize(victimName, MAX_NAME_LENGTH);
        cause = cause == null ? Cause.NONE : cause;
        weaponId = sanitize(weaponId, MAX_WEAPON_ID_LENGTH);
    }

    public KillFeedPacket(FriendlyByteBuf buf) {
        this(buf.readUtf(MAX_NAME_LENGTH), buf.readUtf(MAX_NAME_LENGTH),
                Cause.byId(buf.readVarInt()), buf.readUtf(MAX_WEAPON_ID_LENGTH));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(killerName, MAX_NAME_LENGTH);
        buf.writeUtf(victimName, MAX_NAME_LENGTH);
        buf.writeVarInt(cause.id());
        buf.writeUtf(weaponId, MAX_WEAPON_ID_LENGTH);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> this::invokeClientHandler));
        context.setPacketHandled(true);
    }

    private void invokeClientHandler() {
        try {
            Class<?> handler = Class.forName("com.makar.tacticaltablet.client.KillFeedClientState");
            handler.getMethod("handle", KillFeedPacket.class).invoke(null, this);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Failed to add Tactical kill-feed entry", exception);
        }
    }

    private static String sanitize(String value, int maxLength) {
        if (value == null) return "";
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    public enum Cause {
        NONE(0), BLEEDING(1), FIRE(2), FALL(3);

        private final int id;
        Cause(int id) { this.id = id; }
        public int id() { return id; }
        public static Cause byId(int id) {
            for (Cause cause : values()) if (cause.id == id) return cause;
            return NONE;
        }
    }
}
