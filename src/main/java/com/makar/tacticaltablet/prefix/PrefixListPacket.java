package com.makar.tacticaltablet.prefix;

import net.minecraft.network.FriendlyByteBuf;
import com.makar.tacticaltablet.tablet.net.PacketCodecs;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class PrefixListPacket {

    public static final int MAX_PLAYERS = 256;
    private static final int MAX_STRING_LENGTH = 64;

    private final List<Entry> entries;

    public PrefixListPacket(List<Entry> entries) {
        this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
    }

    public PrefixListPacket(FriendlyByteBuf buf) {
        int size = PacketCodecs.readBoundedVarIntSize(buf, MAX_PLAYERS, "prefix player count");
        this.entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(
                    buf.readUUID(),
                    buf.readUtf(MAX_STRING_LENGTH),
                    buf.readUtf(MAX_STRING_LENGTH),
                    buf.readUtf(MAX_STRING_LENGTH),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt()
            ));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        int size = Math.min(entries.size(), MAX_PLAYERS);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            Entry entry = entries.get(i);
            buf.writeUUID(entry.uuid());
            buf.writeUtf(limit(entry.playerName()), MAX_STRING_LENGTH);
            buf.writeUtf(limit(entry.roleId()), MAX_STRING_LENGTH);
            buf.writeUtf(limit(entry.displayName()), MAX_STRING_LENGTH);
            buf.writeInt(entry.textColor());
            buf.writeInt(entry.backgroundColor());
            buf.writeInt(entry.priority());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> PrefixClientState.update(entries.stream()
                .map(entry -> new PrefixClientState.Entry(
                        entry.uuid(),
                        entry.playerName(),
                        entry.roleId(),
                        entry.displayName(),
                        entry.textColor(),
                        entry.backgroundColor(),
                        entry.priority()
                ))
                .toList()));
        ctx.get().setPacketHandled(true);
    }

    private static String limit(String value) {
        if (value == null) return "";
        return value.length() <= MAX_STRING_LENGTH ? value : value.substring(0, MAX_STRING_LENGTH);
    }

    public record Entry(
            UUID uuid,
            String playerName,
            String roleId,
            String displayName,
            int textColor,
            int backgroundColor,
            int priority
    ) {
    }
}
