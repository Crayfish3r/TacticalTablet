package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.game.arena.ArenaSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.function.Supplier;

public record ArenaStatePacket(ArenaSnapshot snapshot) {
    public static final int MAX_ROWS = 4096;
    public ArenaStatePacket(FriendlyByteBuf buf) { this(read(buf)); }
    private static ArenaSnapshot read(FriendlyByteBuf buf) {
        var id = buf.readBoolean() ? buf.readUUID() : null;
        boolean active = buf.readBoolean(), finished = buf.readBoolean();
        int left = Math.max(0, Math.min(900, buf.readVarInt()));
        int game = Math.max(0, Math.min(3, buf.readVarInt()));
        int games = buf.readVarInt();
        int size = PacketCodecs.readBoundedIntSize(buf, MAX_ROWS, "Arena results");
        var rows = new ArrayList<ArenaSnapshot.Row>();
        for (int i = 0; i < size; i++) rows.add(new ArenaSnapshot.Row(buf.readUUID(), buf.readUtf(64),
                Math.max(0, buf.readVarInt()), Math.max(0, buf.readVarInt()), Math.max(0, buf.readVarInt()),
                Math.max(0, buf.readVarInt()), Math.max(0, buf.readVarInt())));
        return new ArenaSnapshot(id, active, finished, left, game, Math.max(1, Math.min(3, games)), rows, buf.readBoolean());
    }
    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(snapshot.matchId() != null);
        if (snapshot.matchId() != null) buf.writeUUID(snapshot.matchId());
        buf.writeBoolean(snapshot.active()); buf.writeBoolean(snapshot.finished());
        buf.writeVarInt(snapshot.secondsLeft()); buf.writeVarInt(snapshot.gameNumber()); buf.writeVarInt(snapshot.gamesPerSet());
        var rows = snapshot.rows().stream().limit(MAX_ROWS).toList();
        buf.writeInt(rows.size());
        for (var row : rows) {
            buf.writeUUID(row.playerId()); buf.writeUtf(row.name().substring(0, Math.min(64, row.name().length())), 64);
            buf.writeVarInt(row.score()); buf.writeVarInt(row.pvpKills()); buf.writeVarInt(row.npcKills());
            buf.writeVarInt(row.assists()); buf.writeVarInt(row.deaths());
        }
        buf.writeBoolean(snapshot.setResults());
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.makar.tacticaltablet.tablet.client.ArenaClientState.update(snapshot)));
        context.setPacketHandled(true);
    }
}
