package com.makar.tacticaltablet.clan;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClanKickMemberPacket {

    private final String clanId;
    private final String memberUuid;

    public ClanKickMemberPacket(String clanId, String memberUuid) {
        this.clanId = clanId == null ? "" : clanId;
        this.memberUuid = memberUuid == null ? "" : memberUuid;
    }

    public ClanKickMemberPacket(FriendlyByteBuf buf) {
        this.clanId = buf.readUtf(ClanConstants.MAX_ID_LENGTH);
        this.memberUuid = buf.readUtf(ClanConstants.MAX_ID_LENGTH);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(limit(clanId), ClanConstants.MAX_ID_LENGTH);
        buf.writeUtf(limit(memberUuid), ClanConstants.MAX_ID_LENGTH);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ClanManager.Result result = ClanManager.kickMember(player, clanId, memberUuid);
            player.sendSystemMessage(Component.literal(message(result)));
        });
        ctx.get().setPacketHandled(true);
    }

    private static String message(ClanManager.Result result) {
        return switch (result) {
            case CLAN_WAR_LOCKED -> "[WAR] Во время войны кланов нельзя исключать игроков из клана.";
            case SUCCESS -> "[WAR] Игрок исключен из клана.";
            case NOT_OWNER -> "[WAR] Только глава клана может выполнить это действие.";
            case CANNOT_KICK_OWNER -> "[WAR] Нельзя исключить главу клана.";
            case NOT_FOUND -> "[WAR] Игрок не найден.";
            default -> "[WAR] Игрок не исключен.";
        };
    }

    private static String limit(String value) {
        if (value == null) return "";
        return value.length() <= ClanConstants.MAX_ID_LENGTH ? value : value.substring(0, ClanConstants.MAX_ID_LENGTH);
    }
}
