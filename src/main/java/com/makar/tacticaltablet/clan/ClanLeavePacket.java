package com.makar.tacticaltablet.clan;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClanLeavePacket {

    public ClanLeavePacket() {
    }

    public ClanLeavePacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ClanManager.Result result = ClanManager.leaveCurrentClan(player);
            player.sendSystemMessage(Component.literal(message(result)));
        });
        ctx.get().setPacketHandled(true);
    }

    private static String message(ClanManager.Result result) {
        return switch (result) {
            case CLAN_WAR_LOCKED -> "[WAR] Во время войны кланов нельзя выходить из клана.";
            case SUCCESS -> "[WAR] Вы вышли из клана.";
            case OWNER_CANNOT_LEAVE -> "[WAR] Владелец не может выйти из клана. Распустите клан.";
            case NOT_FOUND -> "[WAR] Клан не найден.";
            default -> "[WAR] Выход из клана не выполнен.";
        };
    }
}
