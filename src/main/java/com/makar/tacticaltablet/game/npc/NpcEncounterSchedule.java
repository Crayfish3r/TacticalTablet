package com.makar.tacticaltablet.game.npc;

import com.makar.tacticaltablet.game.lifecycle.MatchLifecycleSnapshot;
import com.makar.tacticaltablet.game.lifecycle.MatchState;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** Tick clock and conservative capacity reservations. Unloaded NPCs retain their slot. */
final class NpcEncounterSchedule {
    private UUID matchId;
    private boolean active;
    private long due;
    private final Set<UUID> owned = new HashSet<>();

    void start(UUID id, long now, NpcEncounterConfig c, RandomGenerator random) {
        if (matchId != null) throw new IllegalStateException("Previous NPC session requires cleanup");
        matchId = id;
        active = true;
        defer(now, c, random);
    }

    boolean running(MatchLifecycleSnapshot snapshot, boolean combatRunning) {
        return active && combatRunning && snapshot.state() == MatchState.RUNNING
                && snapshot.matchId().filter(id -> id.equals(matchId)).isPresent();
    }

    boolean due(long now) { return active && now >= due; }
    UUID matchId() { return matchId; }
    void deactivate() { active = false; due = Long.MAX_VALUE; }
    int available(int limit, int loadedAlive) { return Math.max(0, limit - Math.max(owned.size(), loadedAlive)); }
    void spawned(java.util.Collection<UUID> ids) { owned.addAll(ids); }
    void removed(UUID id) { owned.remove(id); }
    boolean owns(UUID id) { return owned.contains(id); }
    void clear() { deactivate(); matchId = null; owned.clear(); }
    void defer(long now, NpcEncounterConfig c, RandomGenerator random) {
        due = now + 20L * random.nextInt(c.minSpawnIntervalSeconds, c.maxSpawnIntervalSeconds + 1);
    }
}
