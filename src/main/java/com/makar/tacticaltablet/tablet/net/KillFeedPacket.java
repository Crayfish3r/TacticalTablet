package com.makar.tacticaltablet.tablet.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record KillFeedPacket(UUID killerUuid, String killerName, int killerColor,
                             UUID victimUuid, String victimName, int victimColor,
                             Cause cause, String weaponDisplayName, long serverTime,
                             int awardedCoins, int awardedXp) {
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_WEAPON_NAME_LENGTH = 48;
    public static final int NO_TEAM_COLOR = -1;

    public KillFeedPacket {
        killerName = sanitize(killerName, MAX_NAME_LENGTH);
        victimName = sanitize(victimName, MAX_NAME_LENGTH);
        cause = cause == null ? Cause.NONE : cause;
        weaponDisplayName = sanitize(weaponDisplayName, MAX_WEAPON_NAME_LENGTH);
        awardedCoins = Math.max(0, awardedCoins);
        awardedXp = Math.max(0, awardedXp);
        if (victimUuid == null) throw new IllegalArgumentException("Kill feed requires a victim UUID");
    }

    public KillFeedPacket(FriendlyByteBuf buf) {
        this(readOptionalUuid(buf), buf.readUtf(MAX_NAME_LENGTH), buf.readInt(), buf.readUUID(),
                buf.readUtf(MAX_NAME_LENGTH), buf.readInt(), Cause.byId(buf.readVarInt()),
                buf.readUtf(MAX_WEAPON_NAME_LENGTH), buf.readLong(), buf.readVarInt(), buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        writeOptionalUuid(buf, killerUuid);
        buf.writeUtf(killerName, MAX_NAME_LENGTH);
        buf.writeInt(killerColor);
        buf.writeUUID(victimUuid);
        buf.writeUtf(victimName, MAX_NAME_LENGTH);
        buf.writeInt(victimColor);
        buf.writeVarInt(cause.id());
        buf.writeUtf(weaponDisplayName, MAX_WEAPON_NAME_LENGTH);
        buf.writeLong(serverTime);
        buf.writeVarInt(awardedCoins);
        buf.writeVarInt(awardedXp);
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

    private static void writeOptionalUuid(FriendlyByteBuf buf, UUID uuid) {
        buf.writeBoolean(uuid != null);
        if (uuid != null) buf.writeUUID(uuid);
    }

    private static UUID readOptionalUuid(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUUID() : null;
    }

    private static String sanitize(String value, int maxLength) {
        if (value == null) return "";
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    public enum Cause {
        NONE(0), BLEEDING(1), FIRE(2), FALL(3), LAVA(4), ZONE(5);

        private final int id;
        Cause(int id) { this.id = id; }
        public int id() { return id; }
        public static Cause byId(int id) {
            for (Cause cause : values()) if (cause.id == id) return cause;
            return NONE;
        }
    }
}
