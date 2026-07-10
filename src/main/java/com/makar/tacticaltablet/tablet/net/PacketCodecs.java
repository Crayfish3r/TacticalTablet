package com.makar.tacticaltablet.tablet.net;

import net.minecraft.network.FriendlyByteBuf;

/** Bounds and enum decoding helpers for all Tactical Tablet packets. */
public final class PacketCodecs {

    private PacketCodecs() { }

    public static int readBoundedIntSize(FriendlyByteBuf buf, int max, String field) {
        return requireSize(buf.readInt(), max, field);
    }

    public static int readBoundedVarIntSize(FriendlyByteBuf buf, int max, String field) {
        return requireSize(buf.readVarInt(), max, field);
    }

    public static int requireSize(int size, int max, String field) {
        if (size < 0 || size > max) {
            throw new IllegalArgumentException("Invalid " + field + " size: " + size + " (max " + max + ")");
        }
        return size;
    }

    public static <E extends Enum<E>> E readEnumOrdinal(FriendlyByteBuf buf, E[] values, String field) {
        int ordinal = buf.readByte();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid " + field + " ordinal: " + ordinal);
        }
        return values[ordinal];
    }

    public static <E extends Enum<E>> E readOptionalEnumOrdinal(FriendlyByteBuf buf, E[] values, String field) {
        int ordinal = buf.readByte();
        if (ordinal == -1) return null;
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid " + field + " ordinal: " + ordinal);
        }
        return values[ordinal];
    }
}
