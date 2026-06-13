package com.makar.tacticaltablet.game.team;

import net.minecraft.ChatFormatting;

public enum TeamId {
    ALFA("Альфа", ChatFormatting.RED, 0xFFFF5555),
    BETA("Бета", ChatFormatting.BLUE, 0xFF5599FF),
    GAMMA("Гамма", ChatFormatting.GREEN, 0xFF55FF55),
    DELTA("Дельта", ChatFormatting.YELLOW, 0xFFFFFF55);

    private final String displayName;
    private final ChatFormatting chatColor;
    private final int textColor;

    TeamId(String displayName, ChatFormatting chatColor, int textColor) {
        this.displayName = displayName;
        this.chatColor = chatColor;
        this.textColor = textColor;
    }

    public String displayName() {
        return displayName;
    }

    public ChatFormatting chatColor() {
        return chatColor;
    }

    public int textColor() {
        return textColor;
    }

    public String scoreboardName() {
        return "tt_" + name().toLowerCase(java.util.Locale.ROOT);
    }

    public static TeamId byId(int id) {
        TeamId[] values = values();
        if (id < 0 || id >= values.length) {
            throw new IllegalArgumentException("Invalid team id: " + id);
        }
        return values[id];
    }
}
