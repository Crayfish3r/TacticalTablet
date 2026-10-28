package com.makar.tacticaltablet.tablet.net;
import com.makar.tacticaltablet.game.team.ArenaTeamService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;
public record ArenaTeamStatePacket(UUID session, long requestId, UUID matchId, long revision, boolean arena, int own,
        String reason, ArenaTeamService.Result result, List<Member> members) {
    public record Member(int team, UUID id, String name, String status) { }
    public ArenaTeamStatePacket {
        if (requestId <= 0 || revision <= 0 || own < -1 || own > 3 || members.size() > 20 || reason.length() > 192)
            throw new IllegalArgumentException("Invalid arena team snapshot");
        members = List.copyOf(members);
    }
    public ArenaTeamStatePacket(FriendlyByteBuf b) { this(b.readUUID(), b.readVarLong(), b.readUUID(), b.readVarLong(), b.readBoolean(), b.readVarInt(), b.readUtf(192), b.readEnum(ArenaTeamService.Result.class), readMembers(b)); }
    private static List<Member> readMembers(FriendlyByteBuf b) {
        int count = b.readVarInt(); if (count < 0 || count > 20) throw new IllegalArgumentException("Too many arena members");
        var list = new ArrayList<Member>(); var ids = new HashSet<UUID>(); int[] sizes = new int[4];
        for (int i = 0; i < count; i++) {
            int team = b.readVarInt(); UUID id = b.readUUID();
            if (team < 0 || team > 3 || ++sizes[team] > 5 || !ids.add(id)) throw new IllegalArgumentException("Invalid arena roster");
            list.add(new Member(team, id, b.readUtf(32), b.readUtf(32)));
        } return list;
    }
    public void encode(FriendlyByteBuf b) {
        b.writeUUID(session); b.writeVarLong(requestId); b.writeUUID(matchId); b.writeVarLong(revision); b.writeBoolean(arena); b.writeVarInt(own);
        b.writeUtf(reason, 192); b.writeEnum(result); if (members.size() > 20) throw new IllegalArgumentException("Too many members"); b.writeVarInt(members.size());
        for (Member m : members) { b.writeVarInt(m.team); b.writeUUID(m.id); b.writeUtf(m.name, 32); b.writeUtf(m.status, 32); }
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get(); c.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.makar.tacticaltablet.tablet.client.prototype.TabletArenaTeamsPage.accept(this))); c.setPacketHandled(true);
    }
}
