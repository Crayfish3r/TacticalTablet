package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MapSetManager;
import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.inventory.InventoryManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class VoteMapPacket {

    private static final int MAX_MAP_NAME_LENGTH = 64;
    private final String mapName;

    public VoteMapPacket(String mapName) {
        this.mapName = mapName == null ? "" : mapName;
    }

    public VoteMapPacket(FriendlyByteBuf buf) {
        this.mapName = buf.readUtf(MAX_MAP_NAME_LENGTH);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(mapName, MAX_MAP_NAME_LENGTH);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (!PacketHandler.allowC2S(player, PacketHandler.C2SAction.VOTE)) {
                MapSetManager.sync(player, false);
                return;
            }
            if (!InventoryManager.hasTablet(player)) {
                MapSetManager.sync(player, false);
                return;
            }
            if (GameStateManager.getMatchPhase() != MatchPhase.MAP_VOTING) {
                MapSetManager.sync(player, false);
                return;
            }
            MapSetManager.vote(player, mapName);
        });
        context.setPacketHandled(true);
    }
}
