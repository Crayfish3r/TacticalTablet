package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.clan.ClanConstants;

/** Pure mirror of client-side clan form constraints; server validation remains authoritative. */
final class ClanCreateInputPolicy {
    private ClanCreateInputPolicy() {
    }

    static boolean isValid(
            String name,
            String tag,
            int clanCount,
            boolean colorAvailable,
            long coins
    ) {
        String normalizedName = name == null ? "" : name.trim();
        String normalizedTag = tag == null ? "" : tag.trim();
        return normalizedName.length() >= 3
                && normalizedName.length() <= ClanConstants.MAX_NAME_LENGTH
                && !normalizedTag.isBlank()
                && normalizedTag.length() <= ClanConstants.MAX_TAG_LENGTH
                && clanCount < ClanConstants.MAX_CLANS
                && colorAvailable
                && coins >= ClanConstants.CREATE_COST;
    }
}
