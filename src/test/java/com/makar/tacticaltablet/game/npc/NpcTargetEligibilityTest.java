package com.makar.tacticaltablet.game.npc;

import com.makar.tacticaltablet.game.lifecycle.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NpcTargetEligibilityTest {
    @Test void realLifecycleMembershipAndEveryLiveEligibilityConditionAreRequired() {
        var participant = UUID.randomUUID(); var outsider = UUID.randomUUID(); var lifecycle = new MatchLifecycleService();
        var request = new MatchStartRequest("map", "SOLO", "test", null, Set.of(participant), Instant.EPOCH);
        var match = lifecycle.beginPreparation(request).matchId().orElseThrow();
        lifecycle.markStarting(match);
        boolean[] eligible = {true, true, true, true, true, false, false, false, true};
        assertFalse(check(match, lifecycle.snapshot(), participant, eligible));
        lifecycle.markRunning(match); assertTrue(check(match, lifecycle.snapshot(), participant, eligible));
        assertFalse(check(match, lifecycle.snapshot(), outsider, eligible));
        assertFalse(check(UUID.randomUUID(), lifecycle.snapshot(), participant, eligible));
        for (int i = 0; i < eligible.length; i++) {
            var changed = eligible.clone(); changed[i] = !changed[i];
            assertFalse(check(match, lifecycle.snapshot(), participant, changed), "Eligibility flag " + i);
        }
        lifecycle.beginEnding(match, MatchEndReason.FORCED);
        assertFalse(check(match, lifecycle.snapshot(), participant, eligible));
        lifecycle.beginCleanup(match); lifecycle.markIdle(match);
        UUID next = lifecycle.beginPreparation(request).matchId().orElseThrow();
        lifecycle.markStarting(next); lifecycle.markRunning(next);
        assertFalse(check(match, lifecycle.snapshot(), participant, eligible));
    }

    private static boolean check(UUID match, MatchLifecycleSnapshot snapshot, UUID player, boolean[] p) {
        return NpcTargetEligibility.canAttack(match, snapshot, player, p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8]);
    }
}
