package com.makar.tacticaltablet.clan;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClanDisbandPacket {

    private final String clanId;

    public ClanDisbandPacket(String clanId) {
        this.clanId = clanId == null ? "" : clanId;
    }

    public ClanDisbandPacket(FriendlyByteBuf buf) {
        this.clanId = buf.readUtf(ClanConstants.MAX_ID_LENGTH);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(limit(clanId), ClanConstants.MAX_ID_LENGTH);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ClanManager.Result result = ClanManager.disbandClan(player, clanId);
            player.sendSystemMessage(Component.literal(message(result)));
        });
        ctx.get().setPacketHandled(true);
    }

    private static String message(ClanManager.Result result) {
        return switch (result) {
            case CLAN_WAR_LOCKED -> "[WAR] Во время войны кланов нельзя распускать клан.";
            case SUCCESS -> "[WAR] Клан распущен.";
            case NOT_OWNER -> "[WAR] Только глава клана может выполнить это действие.";
            case NOT_FOUND -> "[WAR] Клан не найден.";
            default -> "[WAR] Клан не распущен.";
        };
    }

    private static String limit(String value) {
        if (value == null) return "";
        return value.length() <= ClanConstants.MAX_ID_LENGTH ? value : value.substring(0, ClanConstants.MAX_ID_LENGTH);
    }
}
