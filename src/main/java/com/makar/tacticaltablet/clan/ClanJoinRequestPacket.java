package com.makar.tacticaltablet.clan;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClanJoinRequestPacket {

    private final String clanId;

    public ClanJoinRequestPacket(String clanId) {
        this.clanId = clanId == null ? "" : clanId;
    }

    public ClanJoinRequestPacket(FriendlyByteBuf buf) {
        this.clanId = buf.readUtf(ClanConstants.MAX_ID_LENGTH);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(limit(clanId), ClanConstants.MAX_ID_LENGTH);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ClanManager.Result result = ClanManager.requestJoin(player, clanId);
            player.sendSystemMessage(Component.literal(message(result)));
        });
        ctx.get().setPacketHandled(true);
    }

    private static String message(ClanManager.Result result) {
        return switch (result) {
            case RATE_LIMITED -> "[WAR] Подождите несколько секунд перед следующей заявкой.";
            case PENDING_LIMIT_REACHED -> "[WAR] У вас слишком много активных заявок в кланы.";
            case CLAN_PENDING_FULL -> "[WAR] В этом клане слишком много неразобранных заявок.";
            case SUCCESS -> "[WAR] Заявка отправлена.";
            case ALREADY_PENDING -> "[WAR] Заявка уже отправлена.";
            case ALREADY_IN_CLAN -> "[WAR] Вы уже состоите в клане.";
            case NOT_FOUND -> "[WAR] Клан не найден.";
            default -> "[WAR] Заявка не отправлена.";
        };
    }

    private static String limit(String value) {
        if (value == null) return "";
        return value.length() <= ClanConstants.MAX_ID_LENGTH ? value : value.substring(0, ClanConstants.MAX_ID_LENGTH);
    }
}
