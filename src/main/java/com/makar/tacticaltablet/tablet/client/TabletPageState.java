package com.makar.tacticaltablet.tablet.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Screen-independent navigation, selection and viewport state for the tablet shell. */
final class TabletPageState {
    static final String SERVER_INFO = "server_info";
    static final String CLAN_LIST = "clan.list";
    static final String CLAN_MEMBERS = "clan.members";
    static final String CLAN_PENDING = "clan.pending";

    private final Map<String, Integer> offsets = new HashMap<>();
    private int currentPage;
    private int selectedClanIndex = -1;

    int currentPage() {
        return currentPage;
    }

    boolean selectPage(int pageIndex, int pageCount) {
        if (pageIndex < 0 || pageIndex >= pageCount || pageIndex == currentPage) return false;
        currentPage = pageIndex;
        return true;
    }

    int selectedClanIndex() {
        return selectedClanIndex;
    }

    void selectClan(int index) {
        selectedClanIndex = Math.max(-1, index);
        reset(CLAN_MEMBERS, CLAN_PENDING);
    }

    boolean clampSelectedClan(int clanCount) {
        int before = selectedClanIndex;
        selectedClanIndex = Math.max(-1, Math.min(clanCount - 1, selectedClanIndex));
        if (selectedClanIndex < 0) reset(CLAN_MEMBERS);
        return before != selectedClanIndex;
    }

    int offset(String key) {
        return offsets.getOrDefault(Objects.requireNonNull(key, "key"), 0);
    }

    int clampOffset(String key, int maximum) {
        int clamped = Math.max(0, Math.min(Math.max(0, maximum), offset(key)));
        offsets.put(key, clamped);
        return clamped;
    }

    int scroll(String key, int delta, int maximum) {
        offsets.put(Objects.requireNonNull(key, "key"), offset(key) + delta);
        return clampOffset(key, maximum);
    }

    void reset(String... keys) {
        for (String key : keys) offsets.remove(Objects.requireNonNull(key, "key"));
    }
}
