package com.makar.tacticaltablet.prefix;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class PrefixDisplayHelper {

    private PrefixDisplayHelper() {
    }

    public static Component buildBadge(PrefixRole role) {
        return role == null ? Component.empty() : role.buildBadge();
    }

    public static Component buildBadge(String displayName, int textColor, int backgroundColor) {
        if (displayName == null || displayName.isBlank()) {
            return Component.empty();
        }

        Style bracketStyle = Style.EMPTY.withColor(TextColor.fromRgb(backgroundColor & 0xFFFFFF));
        Style textStyle = Style.EMPTY
                .withColor(TextColor.fromRgb(textColor & 0xFFFFFF))
                .withBold(true);

        return Component.literal(" [").withStyle(bracketStyle)
                .append(Component.literal(displayName).withStyle(textStyle))
                .append(Component.literal("]").withStyle(bracketStyle));
    }

    public static Component appendSuffix(Component baseName, PrefixRole role) {
        return appendSuffix(baseName, buildBadge(role));
    }

    public static Component appendSuffix(Component baseName, Component badge) {
        MutableComponent result = baseName == null
                ? Component.empty()
                : baseName.copy();

        if (badge == null || badge.getString().isBlank()) {
            return result;
        }

        return result.append(badge);
    }

    public static Component plainSystem(String text) {
        return Component.literal(text == null ? "" : text).withStyle(ChatFormatting.GRAY);
    }
}
