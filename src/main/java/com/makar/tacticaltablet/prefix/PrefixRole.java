package com.makar.tacticaltablet.prefix;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

public enum PrefixRole {
    NONE("none", "", 0, 0xFFFFFF, 0x000000, Set.of()),
    OWNER("owner", "OWNER", 1000, 0xFFFFFF, 0xFF3333, Set.of(
            PrefixPermission.ADMIN,
            PrefixPermission.PREFIX_MANAGE,
            PrefixPermission.PREFIX_RELOAD,
            PrefixPermission.PREFIX_LIST,
            PrefixPermission.DEBUG,
            "tacticaltablet.*"
    )),
    MODER("moder", "MODER", 500, 0xFFFFFF, 0xFFAA00, Set.of(
            PrefixPermission.MODER_MODE,
            PrefixPermission.MOD_MUTE,
            PrefixPermission.MOD_UNMUTE,
            PrefixPermission.MOD_KICK,
            PrefixPermission.MOD_TEMPBAN,
            PrefixPermission.MOD_UNBAN,
            PrefixPermission.MOD_PUNISHMENTS
    )),
    BUILDER("builder", "BUILDER", 300, 0xFFFFFF, 0x55FF55, Set.of(
            PrefixPermission.BUILD
    )),
    ELITE("elite", "ELITE", 200, 0xFFFFFF, 0xAA55FF, Set.of(
            "tacticaltablet.prefix.elite"
    )),
    PRO("pro", "PRO", 100, 0xFFFFFF, 0x5599FF, Set.of(
            "tacticaltablet.prefix.pro"
    ));

    private final String id;
    private final String displayName;
    private final int priority;
    private final int textColor;
    private final int backgroundColor;
    private final Set<String> permissions;

    PrefixRole(String id, String displayName, int priority, int textColor, int backgroundColor, Set<String> permissions) {
        this.id = id;
        this.displayName = displayName;
        this.priority = priority;
        this.textColor = textColor;
        this.backgroundColor = backgroundColor;
        this.permissions = permissions;
    }

    public static PrefixRole byId(String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("creator") || normalized.equals("создатель")) return OWNER;
        if (normalized.equals("moderator") || normalized.equals("модератор") || normalized.equals("модер")) return MODER;
        if (normalized.equals("строитель")) return BUILDER;
        if (normalized.equals("элита")) return ELITE;
        if (normalized.equals("про")) return PRO;
        if (normalized.equals("clear") || normalized.equals("нет")) return NONE;

        return Arrays.stream(values())
                .filter(role -> role.id.equals(normalized))
                .findFirst()
                .orElse(NONE);
    }

    public boolean hasPermission(String permission) {
        if (permission == null || permission.isBlank()) return false;

        for (String granted : permissions) {
            if (PrefixPermission.matches(granted, permission)) {
                return true;
            }
        }
        return false;
    }

    public boolean isAtLeast(PrefixRole other) {
        PrefixRole target = other == null ? NONE : other;
        return priority >= target.priority;
    }

    public Component buildBadge() {
        if (!visible()) {
            return Component.empty();
        }

        return PrefixDisplayHelper.buildBadge(displayName, textColor, backgroundColor);
    }

    public Component buildSuffixBadge() {
        return buildBadge();
    }

    public boolean visible() {
        return this != NONE && !displayName.isBlank();
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int priority() {
        return priority;
    }

    public int textColor() {
        return textColor;
    }

    public int backgroundColor() {
        return backgroundColor;
    }

    public Set<String> permissions() {
        return permissions;
    }

    public ChatFormatting fallbackFormatting() {
        return switch (this) {
            case OWNER -> ChatFormatting.RED;
            case MODER -> ChatFormatting.GOLD;
            case BUILDER -> ChatFormatting.GREEN;
            case ELITE -> ChatFormatting.LIGHT_PURPLE;
            case PRO -> ChatFormatting.BLUE;
            case NONE -> ChatFormatting.WHITE;
        };
    }
}
