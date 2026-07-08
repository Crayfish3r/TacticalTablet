package com.makar.tacticaltablet.prefix;

import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PrefixClientState {

    private static final Map<UUID, Entry> byUuid = new HashMap<>();
    private static final Map<String, Entry> byName = new HashMap<>();

    private PrefixClientState() {
    }

    public static synchronized void update(List<Entry> entries) {
        byUuid.clear();
        byName.clear();

        if (entries == null) return;
        for (Entry entry : entries) {
            if (entry == null || entry.uuid() == null) continue;

            byUuid.put(entry.uuid(), entry);
            if (entry.playerName() != null && !entry.playerName().isBlank()) {
                byName.put(entry.playerName().toLowerCase(Locale.ROOT), entry);
            }
        }
    }

    public static synchronized void clear() {
        byUuid.clear();
        byName.clear();
    }

    public static synchronized Entry get(UUID uuid) {
        return uuid == null ? null : byUuid.get(uuid);
    }

    public static synchronized Entry getByName(String name) {
        return name == null ? null : byName.get(name.toLowerCase(Locale.ROOT));
    }

    public static synchronized PrefixRole getRole(UUID uuid) {
        Entry entry = get(uuid);
        return entry == null ? PrefixRole.NONE : PrefixRole.byId(entry.roleId());
    }

    public static synchronized Component buildBadge(UUID uuid) {
        Entry entry = get(uuid);
        if (entry == null || entry.displayName().isBlank()) {
            return Component.empty();
        }
        return PrefixDisplayHelper.buildBadge(entry.displayName(), entry.textColor(), entry.backgroundColor());
    }

    public record Entry(
            UUID uuid,
            String playerName,
            String roleId,
            String displayName,
            int textColor,
            int backgroundColor,
            int priority
    ) {
    }
}
