package com.makar.tacticaltablet.clan;

import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.inventory.InventoryManager;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClanChangeColorPacket {

    private final String clanId;
    private final int color;

    public ClanChangeColorPacket(String clanId, int color) {
        this.clanId = clanId == null ? "" : clanId;
        this.color = color;
    }

    public ClanChangeColorPacket(FriendlyByteBuf buf) {
        this.clanId = buf.readUtf(ClanConstants.MAX_ID_LENGTH);
        this.color = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(limit(clanId, ClanConstants.MAX_ID_LENGTH), ClanConstants.MAX_ID_LENGTH);
        buf.writeInt(color);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!PacketHandler.allowC2S(player, PacketHandler.C2SAction.CLAN_MUTATION)) return;
            if (!InventoryManager.hasTablet(player)) return;

            ClanManager.Result result = ClanManager.changeClanColor(player, clanId, color);
            player.sendSystemMessage(Component.literal(message(result)));
        });
        ctx.get().setPacketHandled(true);
    }

    private static String message(ClanManager.Result result) {
        return switch (result) {
            case SUCCESS -> "[WAR] Цвет клана изменен.";
            case NOT_OWNER -> "[WAR] Только глава клана может менять цвет.";
            case NOT_ENOUGH_COINS -> "[WAR] Недостаточно КК. Нужно " + ClanManager.CHANGE_COLOR_COST + " КК.";
            case COLOR_TAKEN -> "[WAR] Этот цвет уже занят другим кланом.";
            case NOT_FOUND -> "[WAR] Клан не найден.";
            default -> "[WAR] Цвет клана не изменен.";
        };
    }

    private static String limit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
