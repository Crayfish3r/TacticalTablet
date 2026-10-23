package com.makar.tacticaltablet.game.npc;

import com.makar.tacticaltablet.game.lifecycle.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NpcEncounterScheduleTest {
    @Test void actualLifecycleCannotSpawnBeforeRunningOrAfterEndingOrWithAnotherId() {
        var lifecycle = new MatchLifecycleService();
        var request = new MatchStartRequest("map", "SOLO", "test", null, Set.of(UUID.randomUUID()), Instant.EPOCH);
        UUID id = lifecycle.beginPreparation(request).matchId().orElseThrow();
        var schedule = new NpcEncounterSchedule();
        schedule.start(id, 100, new NpcEncounterConfig(), new Random(1));
        assertFalse(schedule.running(lifecycle.snapshot(), true));
        lifecycle.markStarting(id); assertFalse(schedule.running(lifecycle.snapshot(), true));
        lifecycle.markRunning(id); assertTrue(schedule.running(lifecycle.snapshot(), true));
        assertFalse(schedule.running(lifecycle.snapshot(), false));
        lifecycle.beginEnding(id, MatchEndReason.NATURAL);
        assertFalse(schedule.running(lifecycle.snapshot(), true));
        lifecycle.beginCleanup(id); assertFalse(schedule.running(lifecycle.snapshot(), true));
        lifecycle.markIdle(id); assertFalse(schedule.running(lifecycle.snapshot(), true));
        UUID next = lifecycle.beginPreparation(request).matchId().orElseThrow();
        lifecycle.markStarting(next); lifecycle.markRunning(next);
        assertNotEquals(id, next); assertFalse(schedule.running(lifecycle.snapshot(), true));
    }

    @Test void tickClockSchedulesFirstAndFailedAttemptsWithFullDelay() {
        var c = new NpcEncounterConfig(); c.minSpawnIntervalSeconds = c.maxSpawnIntervalSeconds = 45;
        var schedule = new NpcEncounterSchedule(); var random = new Random(2);
        schedule.start(UUID.randomUUID(), 300, c, random);
        assertFalse(schedule.due(1199)); assertTrue(schedule.due(1200));
        schedule.defer(1200, c, random); // no position / rejected weapon / empty arena / native spawn refusal
        assertFalse(schedule.due(2099)); assertTrue(schedule.due(2100));
        schedule.deactivate(); assertFalse(schedule.due(Long.MAX_VALUE));
    }

    @Test void randomIntervalsStayWithinConfiguredInclusiveBounds() {
        var c = new NpcEncounterConfig(); var s = new NpcEncounterSchedule(); var r = new Random(3);
        s.start(UUID.randomUUID(), 0, c, r);
        for (int i = 0; i < 200; i++) {
            s.defer(10000, c, r);
            assertFalse(s.due(10000 + 899)); assertTrue(s.due(10000 + 1800));
        }
    }

    @Test void unloadedNpcsReserveCapacityUntilConfirmedDestroyedAndCleanupIsRepeatable() {
        var s = new NpcEncounterSchedule(); var id = UUID.randomUUID();
        s.start(id, 0, new NpcEncounterConfig(), new Random(3));
        var one = UUID.randomUUID(); var two = UUID.randomUUID();
        s.spawned(List.of(one, two));
        assertEquals(10, s.available(12, 2)); assertEquals(10, s.available(12, 0));
        assertEquals(0, s.available(1, 0));
        s.removed(UUID.randomUUID()); assertTrue(s.owns(one));
        s.removed(one); s.removed(one); assertEquals(11, s.available(12, 0));
        assertEquals(0, s.available(12, 15));
        assertThrows(IllegalStateException.class, () -> s.start(UUID.randomUUID(), 0, new NpcEncounterConfig(), new Random()));
        s.clear(); s.clear(); assertNull(s.matchId()); assertEquals(12, s.available(12, 0));
        assertFalse(s.due(Long.MAX_VALUE));
        assertFalse(new NpcEncounterSchedule().owns(two)); // restart never restores scheduler/ownership
    }
}
