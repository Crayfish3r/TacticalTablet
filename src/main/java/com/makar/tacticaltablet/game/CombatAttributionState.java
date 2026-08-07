package com.makar.tacticaltablet.game;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Pure UUID/tick store used by the server-thread CombatAttributionLedger. */
final class CombatAttributionState {
    private final Map<UUID, CombatAttributionLedger.Entry> entries = new HashMap<>();

    CombatAttributionLedger.Entry get(UUID victimId) {
        return victimId == null ? null : entries.get(victimId);
    }

    void put(UUID victimId, CombatAttributionLedger.Entry entry) {
        if (victimId != null && entry != null) entries.put(victimId, entry);
    }

    Optional<CombatAttributionLedger.Entry> findFresh(UUID victimId, int currentTick, int windowTicks) {
        CombatAttributionLedger.Entry entry = get(victimId);
        if (entry == null) return Optional.empty();
        int age = currentTick - entry.serverTick();
        if (age < 0 || age > Math.max(0, windowTicks)) {
            clear(victimId);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    void clear(UUID victimId) {
        if (victimId != null) entries.remove(victimId);
    }

    void reset() {
        entries.clear();
    }
}
