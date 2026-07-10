package com.makar.tacticaltablet.clan;

import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.inventory.InventoryManager;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClanCreatePacket {

    private final String name;
    private final int color;
    private final String tag;

    public ClanCreatePacket(String name, int color, String tag) {
        this.name = name == null ? "" : name;
        this.color = color;
        this.tag = tag == null ? "" : tag;
    }

    public ClanCreatePacket(FriendlyByteBuf buf) {
        this.name = buf.readUtf(ClanConstants.MAX_NAME_LENGTH);
        this.color = buf.readInt();
        this.tag = buf.readUtf(ClanConstants.MAX_TAG_LENGTH);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(limit(name, ClanConstants.MAX_NAME_LENGTH), ClanConstants.MAX_NAME_LENGTH);
        buf.writeInt(color);
        buf.writeUtf(limit(tag, ClanConstants.MAX_TAG_LENGTH), ClanConstants.MAX_TAG_LENGTH);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!PacketHandler.allowC2S(player, PacketHandler.C2SAction.CLAN_MUTATION)) return;
            if (!InventoryManager.hasTablet(player)) return;

            ClanManager.Result result = ClanManager.createClan(player, name, color, tag);
            player.sendSystemMessage(Component.literal(message(result)));
        });
        ctx.get().setPacketHandled(true);
    }

    private static String message(ClanManager.Result result) {
        return switch (result) {
            case SUCCESS -> "[WAR] Клан создан.";
            case NOT_ENOUGH_COINS -> "[WAR] Недостаточно монет для создания клана.";
            case ALREADY_IN_CLAN -> "[WAR] Вы уже состоите в клане.";
            case NAME_TAKEN -> "[WAR] Название или тег уже заняты.";
            case CLAN_LIMIT_REACHED -> "[WAR] Clan limit reached: " + ClanConstants.MAX_CLANS + ".";
            case COLOR_TAKEN -> "[WAR] This clan color is already taken.";
            default -> "[WAR] Клан не создан.";
        };
    }

    private static String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
