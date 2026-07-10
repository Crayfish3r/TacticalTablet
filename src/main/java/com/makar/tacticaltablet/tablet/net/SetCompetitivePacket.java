package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MapSetManager;
import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.inventory.InventoryManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class SetCompetitivePacket {

    private final boolean competitive;

    public SetCompetitivePacket(boolean competitive) {
        this.competitive = competitive;
    }

    public SetCompetitivePacket(FriendlyByteBuf buf) {
        competitive = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(competitive);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.hasPermissions(2)) return;
            if (!PacketHandler.allowC2S(player, PacketHandler.C2SAction.ADMIN_MAP)) {
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
            MapSetManager.setNextSetCompetitive(player, competitive);
        });
        context.setPacketHandled(true);
    }
}
