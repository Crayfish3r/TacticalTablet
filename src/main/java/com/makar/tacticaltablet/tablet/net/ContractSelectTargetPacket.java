package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.game.contract.ContractManager;
import com.makar.tacticaltablet.game.GameStateManager;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ContractSelectTargetPacket {

    private final UUID targetUuid;

    public ContractSelectTargetPacket(UUID targetUuid) {
        this.targetUuid = targetUuid;
    }

    public ContractSelectTargetPacket(FriendlyByteBuf buf) {
        this.targetUuid = buf.readUUID();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(targetUuid);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !PacketHandler.allowC2S(player, PacketHandler.C2SAction.CONTRACT_SELECT)) return;
            if (!GameStateManager.isRunning(player.server) || !ContractManager.hasTrackerItem(player)) return;
            ContractManager.selectTarget(player, targetUuid);
        });
        ctx.get().setPacketHandled(true);
    }
}
