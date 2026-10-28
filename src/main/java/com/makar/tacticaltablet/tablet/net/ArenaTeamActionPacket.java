package com.makar.tacticaltablet.tablet.net;
import com.makar.tacticaltablet.game.team.ArenaTeamService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;
public record ArenaTeamActionPacket(UUID session, long requestId, UUID matchId, ArenaTeamService.Action action, int team) {
    public ArenaTeamActionPacket(FriendlyByteBuf b) { this(b.readUUID(), b.readVarLong(), b.readUUID(), b.readEnum(ArenaTeamService.Action.class), b.readVarInt()); }
    public ArenaTeamActionPacket {
        if (requestId <= 0 || (action == ArenaTeamService.Action.JOIN ? team < 0 || team > 7 : team != -1)) throw new IllegalArgumentException("Invalid arena team action");
    }
    public void encode(FriendlyByteBuf b) { b.writeUUID(session); b.writeVarLong(requestId); b.writeUUID(matchId); b.writeEnum(action); b.writeVarInt(team); }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var c = supplier.get(); c.enqueueWork(() -> ArenaTeamService.handle(c.getSender(), session, requestId, matchId, action, team)); c.setPacketHandled(true);
    }
}
