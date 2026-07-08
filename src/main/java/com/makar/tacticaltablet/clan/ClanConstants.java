package com.makar.tacticaltablet.clan;

public final class ClanConstants {

    public static final int CREATE_COST = 1000;
    public static final int MAX_NAME_LENGTH = 24;
    public static final int MAX_TAG_LENGTH = 4;
    public static final int MAX_ID_LENGTH = 64;
    public static final int MAX_CLANS = 8;
    public static final int MAX_PENDING = 64;
    public static final int MAX_MEMBERS = 10;
    public static final int[] ALLOWED_COLORS = new int[]{
            0xFFFF5555,
            0xFF5599FF,
            0xFF55FF55,
            0xFFFFFF55,
            0xFFFFA64D,
            0xFFFF66CC,
            0xFFB266FF,
            0xFF111111
    };

    private ClanConstants() {
    }
}
