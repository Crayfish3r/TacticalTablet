package com.makar.tacticaltablet.progression;

import com.makar.tacticaltablet.game.MapSetManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class XpNotifier {

    public static void send(ServerPlayer player, int xp, String reason) {
        if (player == null || xp <= 0 || reason == null || MapSetManager.isCompetitiveSet()) return;
        ChatFormatting reasonColor = ChatFormatting.YELLOW;

        if (reason.contains("mortar")) {
            reasonColor = ChatFormatting.RED;
        } else if (reason.contains("grenade") || reason.contains("mine") || reason.contains("explosion")) {
            reasonColor = ChatFormatting.GOLD;
        } else if (reason.contains("sniper")) {
            reasonColor = ChatFormatting.LIGHT_PURPLE;
        } else if (reason.contains("firearm")) {
            reasonColor = ChatFormatting.AQUA;
        } else if (reason.contains("melee")) {
            reasonColor = ChatFormatting.GREEN;
        }

        Component message = Component.empty()
                .append(Component.literal("+").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(String.valueOf(xp)).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                .append(Component.literal(" опыта").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("  |  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(reason).withStyle(reasonColor));

        player.sendSystemMessage(message);
    }
}

