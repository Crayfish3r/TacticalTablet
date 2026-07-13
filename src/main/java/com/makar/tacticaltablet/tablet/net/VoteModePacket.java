package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.game.MatchMode;
import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.lobby.LobbyManager;
import com.makar.tacticaltablet.game.team.VoteManager;
import com.makar.tacticaltablet.inventory.InventoryManager;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class VoteModePacket {

    private final MatchMode mode;
    public VoteModePacket(MatchMode mode) {
        this.mode = mode == null ? MatchMode.SOLO : mode;
    }

    public VoteModePacket(FriendlyByteBuf buf) {
        int rawModeId = buf.readByte();
        MatchMode[] values = MatchMode.values();
        this.mode = rawModeId >= 0 && rawModeId < values.length ? values[rawModeId] : null;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(mode.ordinal());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!PacketHandler.allowC2S(player, PacketHandler.C2SAction.VOTE)) {
                LobbyManager.sync(player);
                return;
            }
            if (!InventoryManager.hasTablet(player)) {
                LobbyManager.sync(player);
                return;
            }

            if (mode == null) {
                LobbyManager.sync(player);
                return;
            }

            if (GameStateManager.getMatchPhase() != MatchPhase.VOTING) {
                LobbyManager.sync(player);
                return;
            }

            VoteManager.vote(player, mode);
            LobbyManager.sync(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
