package com.makar.tacticaltablet.clan;

import com.makar.tacticaltablet.tablet.client.TabletClientState;
import com.makar.tacticaltablet.tablet.net.PacketCodecs;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ClanListPacket {

    private final List<ClanEntry> clans;

    public ClanListPacket(List<ClanEntry> clans) {
        this.clans = clans == null ? new ArrayList<>() : new ArrayList<>(clans);
    }

    public ClanListPacket(FriendlyByteBuf buf) {
        int size = PacketCodecs.readBoundedVarIntSize(buf, ClanConstants.MAX_CLANS, "clan count");
        this.clans = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String id = buf.readUtf(ClanConstants.MAX_ID_LENGTH);
            String name = buf.readUtf(ClanConstants.MAX_NAME_LENGTH);
            String tag = buf.readUtf(ClanConstants.MAX_TAG_LENGTH);
            int color = buf.readInt();
            String ownerName = buf.readUtf(ClanConstants.MAX_NAME_LENGTH);
            String ownerUuid = buf.readUtf(ClanConstants.MAX_ID_LENGTH);
            int memberCount = buf.readVarInt();
            int clanCoins = buf.readVarInt();
            boolean owner = buf.readBoolean();
            boolean member = buf.readBoolean();
            boolean pending = buf.readBoolean();
            boolean marineUnlocked = buf.readBoolean();

            int pendingSize = PacketCodecs.readBoundedVarIntSize(buf, ClanConstants.MAX_PENDING, "clan pending count");
            List<PendingEntry> pendingEntries = new ArrayList<>();
            for (int j = 0; j < pendingSize; j++) {
                pendingEntries.add(new PendingEntry(
                        buf.readUtf(ClanConstants.MAX_ID_LENGTH),
                        buf.readUtf(ClanConstants.MAX_NAME_LENGTH)
                ));
            }

            int memberSize = PacketCodecs.readBoundedVarIntSize(buf, ClanConstants.MAX_MEMBERS, "clan member count");
            List<MemberEntry> memberEntries = new ArrayList<>();
            for (int j = 0; j < memberSize; j++) {
                memberEntries.add(new MemberEntry(
                        buf.readUtf(ClanConstants.MAX_ID_LENGTH),
                        buf.readUtf(ClanConstants.MAX_NAME_LENGTH)
                ));
            }

            clans.add(new ClanEntry(
                    id,
                    name,
                    tag,
                    color,
                    ownerName,
                    ownerUuid,
                    memberCount,
                    clanCoins,
                    owner,
                    member,
                    pending,
                    marineUnlocked,
                    pendingEntries,
                    memberEntries
            ));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        int size = Math.min(clans.size(), ClanConstants.MAX_CLANS);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            ClanEntry clan = clans.get(i);
            buf.writeUtf(limit(clan.id(), ClanConstants.MAX_ID_LENGTH), ClanConstants.MAX_ID_LENGTH);
            buf.writeUtf(limit(clan.name(), ClanConstants.MAX_NAME_LENGTH), ClanConstants.MAX_NAME_LENGTH);
            buf.writeUtf(limit(clan.tag(), ClanConstants.MAX_TAG_LENGTH), ClanConstants.MAX_TAG_LENGTH);
            buf.writeInt(clan.color());
            buf.writeUtf(limit(clan.ownerName(), ClanConstants.MAX_NAME_LENGTH), ClanConstants.MAX_NAME_LENGTH);
            buf.writeUtf(limit(clan.ownerUuid(), ClanConstants.MAX_ID_LENGTH), ClanConstants.MAX_ID_LENGTH);
            buf.writeVarInt(Math.max(0, clan.memberCount()));
            buf.writeVarInt(Math.max(0, clan.clanCoins()));
            buf.writeBoolean(clan.owner());
            buf.writeBoolean(clan.member());
            buf.writeBoolean(clan.pending());
            buf.writeBoolean(clan.marineUnlocked());

            List<PendingEntry> pendingEntries = clan.pendingEntries() == null ? List.of() : clan.pendingEntries();
            int pendingSize = Math.min(pendingEntries.size(), ClanConstants.MAX_PENDING);
            buf.writeVarInt(pendingSize);
            for (int j = 0; j < pendingSize; j++) {
                PendingEntry pending = pendingEntries.get(j);
                buf.writeUtf(limit(pending.uuid(), ClanConstants.MAX_ID_LENGTH), ClanConstants.MAX_ID_LENGTH);
                buf.writeUtf(limit(pending.name(), ClanConstants.MAX_NAME_LENGTH), ClanConstants.MAX_NAME_LENGTH);
            }

            List<MemberEntry> memberEntries = clan.memberEntries() == null ? List.of() : clan.memberEntries();
            int memberSize = Math.min(memberEntries.size(), ClanConstants.MAX_MEMBERS);
            buf.writeVarInt(memberSize);
            for (int j = 0; j < memberSize; j++) {
                MemberEntry member = memberEntries.get(j);
                buf.writeUtf(limit(member.uuid(), ClanConstants.MAX_ID_LENGTH), ClanConstants.MAX_ID_LENGTH);
                buf.writeUtf(limit(member.name(), ClanConstants.MAX_NAME_LENGTH), ClanConstants.MAX_NAME_LENGTH);
            }
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> TabletClientState.updateClans(clans));
        ctx.get().setPacketHandled(true);
    }

    private static String limit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record ClanEntry(
            String id,
            String name,
            String tag,
            int color,
            String ownerName,
            String ownerUuid,
            int memberCount,
            int clanCoins,
            boolean owner,
            boolean member,
            boolean pending,
            boolean marineUnlocked,
            List<PendingEntry> pendingEntries,
            List<MemberEntry> memberEntries
    ) {
    }

    public record PendingEntry(String uuid, String name) {
    }

    public record MemberEntry(String uuid, String name) {
    }
}
