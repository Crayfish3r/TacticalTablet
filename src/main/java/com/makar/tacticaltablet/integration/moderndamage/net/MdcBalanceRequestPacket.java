package com.makar.tacticaltablet.integration.moderndamage.net;

import com.makar.tacticaltablet.tablet.net.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class MdcBalanceRequestPacket {
    public MdcBalanceRequestPacket() {
    }

    public MdcBalanceRequestPacket(FriendlyByteBuf ignored) {
    }

    public static void encode(MdcBalanceRequestPacket ignored, FriendlyByteBuf buffer) {
    }

    public static void handle(MdcBalanceRequestPacket ignored, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player == null) return;
            MdcBalanceStatePacket.Result result = PacketHandler.allowC2S(player, PacketHandler.C2SAction.ADMIN_MDC)
                    ? MdcBalanceStatePacket.Result.NONE
                    : MdcBalanceStatePacket.Result.RATE_LIMITED;
            PacketHandler.sendToPlayer(player, MdcBalanceStatePacket.forPlayer(player, result));
        });
        context.setPacketHandled(true);
    }
}
