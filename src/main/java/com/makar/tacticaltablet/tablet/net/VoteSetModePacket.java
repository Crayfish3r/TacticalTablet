package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MapSetManager;
import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.game.SetGameMode;
import com.makar.tacticaltablet.inventory.InventoryManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class VoteSetModePacket {
    private final SetGameMode mode;

    public VoteSetModePacket(SetGameMode mode) { this.mode = mode; }
    public VoteSetModePacket(FriendlyByteBuf buf) {
        this.mode = PacketCodecs.readOptionalEnumOrdinal(buf, SetGameMode.values(), "set mode vote");
    }
    public void encode(FriendlyByteBuf buf) { buf.writeByte(mode == null ? -1 : mode.ordinal()); }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || mode == null || !mode.selectable()) return;
            if (!PacketHandler.allowC2S(player, PacketHandler.C2SAction.VOTE)
                    || !InventoryManager.hasTablet(player)
                    || GameStateManager.getMatchPhase() != MatchPhase.MAP_VOTING) {
                MapSetManager.sync(player, false);
                return;
            }
            MapSetManager.voteSetMode(player, mode);
        });
        context.setPacketHandled(true);
    }
}
