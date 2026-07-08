package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MapSetManager;
import com.makar.tacticaltablet.game.MatchPhase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class SetClanWarPacket {

    private final boolean clanWar;

    public SetClanWarPacket(boolean clanWar) {
        this.clanWar = clanWar;
    }

    public SetClanWarPacket(FriendlyByteBuf buf) {
        clanWar = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(clanWar);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.hasPermissions(2)) return;
            if (GameStateManager.getMatchPhase() != MatchPhase.MAP_VOTING) {
                MapSetManager.sync(player, false);
                return;
            }
            MapSetManager.setNextSetClanWar(player, clanWar);
        });
        context.setPacketHandled(true);
    }
}
