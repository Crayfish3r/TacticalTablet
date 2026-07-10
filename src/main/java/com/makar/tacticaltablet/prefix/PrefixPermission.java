package com.makar.tacticaltablet.prefix;

import java.util.Locale;

public final class PrefixPermission {

    public static final String ADMIN = "tacticaltablet.admin";
    public static final String PREFIX_MANAGE = "tacticaltablet.prefix.manage";
    public static final String PREFIX_RELOAD = "tacticaltablet.prefix.reload";
    public static final String PREFIX_LIST = "tacticaltablet.prefix.list";
    public static final String DEBUG = "tacticaltablet.debug";
    public static final String BUILD = "tacticaltablet.build";

    public static final String MODER_MODE = "tacticaltablet.moderation.modermode";
    public static final String MOD_MUTE = "tacticaltablet.moderation.mute";
    public static final String MOD_UNMUTE = "tacticaltablet.moderation.unmute";
    public static final String MOD_KICK = "tacticaltablet.moderation.kick";
    public static final String MOD_TEMPBAN = "tacticaltablet.moderation.tempban";
    public static final String MOD_UNBAN = "tacticaltablet.moderation.unban";
    public static final String MOD_PUNISHMENTS = "tacticaltablet.moderation.punishments";

    private PrefixPermission() {
    }

    public static boolean matches(String granted, String requested) {
        String normalizedGranted = normalize(granted);
        String normalizedRequested = normalize(requested);

        if (normalizedGranted.isBlank() || normalizedRequested.isBlank()) return false;
        if ("*".equals(normalizedGranted)) return true;
        if (normalizedGranted.equals(normalizedRequested)) return true;

        if (normalizedGranted.endsWith(".*")) {
            String prefix = normalizedGranted.substring(0, normalizedGranted.length() - 1);
            return normalizedRequested.startsWith(prefix);
        }

        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
