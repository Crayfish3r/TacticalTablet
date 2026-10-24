package com.makar.tacticaltablet.game.npc;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Owned by one encounter session, never persisted or shared between servers. */
final class NpcDeathClaims {
    private record Key(UUID matchId, UUID victimId) { }
    private final Set<Key> processed = new HashSet<>();

    boolean claim(UUID matchId, UUID victimId) {
        return matchId != null && victimId != null && processed.add(new Key(matchId, victimId));
    }

    void clear() { processed.clear(); }
}
