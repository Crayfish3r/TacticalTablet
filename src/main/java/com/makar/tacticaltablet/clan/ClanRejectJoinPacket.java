package com.makar.tacticaltablet.clan;

import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.inventory.InventoryManager;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClanRejectJoinPacket {

    private final String clanId;
    private final String applicantUuid;

    public ClanRejectJoinPacket(String clanId, String applicantUuid) {
        this.clanId = clanId == null ? "" : clanId;
        this.applicantUuid = applicantUuid == null ? "" : applicantUuid;
    }

    public ClanRejectJoinPacket(FriendlyByteBuf buf) {
        this.clanId = buf.readUtf(ClanConstants.MAX_ID_LENGTH);
        this.applicantUuid = buf.readUtf(ClanConstants.MAX_ID_LENGTH);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(limit(clanId), ClanConstants.MAX_ID_LENGTH);
        buf.writeUtf(limit(applicantUuid), ClanConstants.MAX_ID_LENGTH);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!PacketHandler.allowC2S(player, PacketHandler.C2SAction.CLAN_MUTATION)) return;
            if (!InventoryManager.hasTablet(player)) return;

            ClanManager.Result result = ClanManager.rejectJoin(player, clanId, applicantUuid);
            player.sendSystemMessage(Component.literal(message(result)));
        });
        ctx.get().setPacketHandled(true);
    }

    private static String message(ClanManager.Result result) {
        return switch (result) {
            case SUCCESS -> "[WAR] Заявка отклонена.";
            case NOT_OWNER -> "[WAR] Только глава клана может выполнить это действие.";
            case NOT_FOUND -> "[WAR] Заявка не найдена.";
            default -> "[WAR] Заявка не отклонена.";
        };
    }

    private static String limit(String value) {
        if (value == null) return "";
        return value.length() <= ClanConstants.MAX_ID_LENGTH ? value : value.substring(0, ClanConstants.MAX_ID_LENGTH);
    }
}
