package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.game.contract.ContractManager;
import com.makar.tacticaltablet.game.GameStateManager;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ContractOpenTrackerPacket {

    public ContractOpenTrackerPacket() {
    }

    public ContractOpenTrackerPacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !PacketHandler.allowC2S(player, PacketHandler.C2SAction.TRACKER)) return;
            if (!GameStateManager.isRunning(player.server) || !ContractManager.hasTrackerItem(player)) return;
            ContractManager.onTrackerUsed(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
