package com.makar.tacticaltablet.game.lifecycle;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchLifecycleServiceTest {
    private static final UUID FIRST_MATCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_MATCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PLAYER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void normalLifecycleMovesIdleToRunningAndBackToIdle() {
        MatchLifecycleService service = service(FIRST_MATCH_ID);

        MatchTransitionResult prepared = service.beginPreparation(request());
        assertEquals(MatchTransitionStatus.APPLIED, prepared.status());
        assertEquals(MatchState.IDLE, prepared.previousState());
        assertEquals(MatchState.PREPARING, prepared.currentState());
        assertEquals(FIRST_MATCH_ID, prepared.matchId().orElseThrow());
        assertEquals(1L, prepared.revision());

        assertEquals(MatchState.STARTING, service.markStarting(FIRST_MATCH_ID).currentState());
        assertEquals(MatchState.RUNNING, service.markRunning(FIRST_MATCH_ID).currentState());
        assertEquals(MatchState.ENDING, service.beginEnding(FIRST_MATCH_ID, MatchEndReason.NATURAL).currentState());
        assertEquals(MatchState.CLEANING, service.beginCleanup(FIRST_MATCH_ID).currentState());

        MatchTransitionResult idle = service.markIdle(FIRST_MATCH_ID);
        assertEquals(MatchTransitionStatus.APPLIED, idle.status());
        assertEquals(MatchState.IDLE, idle.currentState());
        assertEquals(FIRST_MATCH_ID, idle.matchId().orElseThrow());
        assertEquals(MatchState.IDLE, service.snapshot().state());
        assertFalse(service.snapshot().matchId().isPresent());
    }

    @Test
    void repeatedStartWhileActiveIsNoOpAndDoesNotAllocateSecondMatch() {
        AtomicInteger idSequence = new AtomicInteger();
        MatchLifecycleService service = new MatchLifecycleService(
                fixedClock(),
                () -> idSequence.incrementAndGet() == 1 ? FIRST_MATCH_ID : SECOND_MATCH_ID
        );

        MatchTransitionResult first = service.beginPreparation(request());
        MatchTransitionResult second = service.beginPreparation(request());

        assertEquals(MatchTransitionStatus.APPLIED, first.status());
        assertEquals(MatchTransitionStatus.NO_OP, second.status());
        assertEquals(FIRST_MATCH_ID, second.matchId().orElseThrow());
        assertEquals(1L, second.revision());
        assertEquals(1, idSequence.get());
    }

    @Test
    void invalidTransitionIsRejectedWithoutChangingRevision() {
        MatchLifecycleService service = service(FIRST_MATCH_ID);
        service.beginPreparation(request());

        MatchTransitionResult rejected = service.markRunning(FIRST_MATCH_ID);

        assertEquals(MatchTransitionStatus.REJECTED, rejected.status());
        assertEquals(MatchState.PREPARING, rejected.currentState());
        assertEquals(1L, rejected.revision());
        assertEquals(MatchState.PREPARING, service.snapshot().state());
    }

    @Test
    void staleMatchIdCannotMutateCurrentContext() {
        MatchLifecycleService service = service(FIRST_MATCH_ID);
        service.beginPreparation(request());

        MatchTransitionResult rejected = service.markStarting(SECOND_MATCH_ID);

        assertEquals(MatchTransitionStatus.REJECTED, rejected.status());
        assertEquals(MatchState.PREPARING, rejected.currentState());
        assertEquals(FIRST_MATCH_ID, rejected.matchId().orElseThrow());
        assertEquals(1L, rejected.revision());
    }

    @Test
    void repeatedTransitionIsNoOp() {
        MatchLifecycleService service = service(FIRST_MATCH_ID);
        service.beginPreparation(request());

        MatchTransitionResult first = service.markStarting(FIRST_MATCH_ID);
        MatchTransitionResult second = service.markStarting(FIRST_MATCH_ID);

        assertEquals(MatchTransitionStatus.APPLIED, first.status());
        assertEquals(MatchTransitionStatus.NO_OP, second.status());
        assertEquals(MatchState.STARTING, second.currentState());
        assertEquals(2L, second.revision());
    }

    @Test
    void failureCanMoveToCleanupAndIdle() {
        MatchLifecycleService service = service(FIRST_MATCH_ID);
        service.beginPreparation(request());
        service.markStarting(FIRST_MATCH_ID);

        MatchTransitionResult failed = service.markFailed(
                FIRST_MATCH_ID,
                MatchFailure.of(MatchFailureStage.STARTING, "datapack function failed")
        );

        assertEquals(MatchTransitionStatus.APPLIED, failed.status());
        assertEquals(MatchState.FAILED, failed.currentState());
        assertTrue(failed.failure().isPresent());

        assertEquals(MatchState.CLEANING, service.beginCleanup(FIRST_MATCH_ID).currentState());
        assertEquals(MatchState.IDLE, service.markIdle(FIRST_MATCH_ID).currentState());
    }

    @Test
    void completedStepsAreIdempotentAndSnapshotIsImmutable() {
        MatchLifecycleService service = service(FIRST_MATCH_ID);
        service.beginPreparation(request());

        MatchTransitionResult recorded = service.recordCompletedStep(FIRST_MATCH_ID, "scoreboard-running");
        MatchTransitionResult duplicate = service.recordCompletedStep(FIRST_MATCH_ID, "scoreboard-running");

        assertEquals(MatchTransitionStatus.APPLIED, recorded.status());
        assertEquals(MatchTransitionStatus.NO_OP, duplicate.status());
        assertEquals(Set.of("scoreboard-running"), service.snapshot().completedLifecycleSteps());
        assertThrows(UnsupportedOperationException.class,
                () -> service.snapshot().completedLifecycleSteps().add("other"));
    }

    @Test
    void startRequestDefensivelyCopiesParticipantIds() {
        Set<UUID> participantIds = new java.util.LinkedHashSet<>();
        participantIds.add(PLAYER_ID);
        MatchStartRequest request = new MatchStartRequest(
                "map-a",
                "solo",
                "test",
                null,
                participantIds,
                Instant.EPOCH
        );
        participantIds.clear();

        MatchLifecycleService service = service(FIRST_MATCH_ID);
        service.beginPreparation(request);

        assertEquals(Set.of(PLAYER_ID), service.snapshot().participantIds());
        assertThrows(UnsupportedOperationException.class,
                () -> service.snapshot().participantIds().add(SECOND_MATCH_ID));
    }

    @Test
    void policyDocumentsAllowedTransitions() {
        assertTrue(MatchTransitionPolicy.canTransition(MatchState.IDLE, MatchState.PREPARING));
        assertTrue(MatchTransitionPolicy.canTransition(MatchState.PREPARING, MatchState.STARTING));
        assertTrue(MatchTransitionPolicy.canTransition(MatchState.STARTING, MatchState.RUNNING));
        assertTrue(MatchTransitionPolicy.canTransition(MatchState.RUNNING, MatchState.ENDING));
        assertTrue(MatchTransitionPolicy.canTransition(MatchState.ENDING, MatchState.CLEANING));
        assertTrue(MatchTransitionPolicy.canTransition(MatchState.CLEANING, MatchState.IDLE));
        assertTrue(MatchTransitionPolicy.canTransition(MatchState.FAILED, MatchState.CLEANING));

        assertFalse(MatchTransitionPolicy.canTransition(MatchState.IDLE, MatchState.RUNNING));
        assertFalse(MatchTransitionPolicy.canTransition(MatchState.IDLE, MatchState.CLEANING));
        assertFalse(MatchTransitionPolicy.canTransition(MatchState.RUNNING, MatchState.STARTING));
    }

    private static MatchLifecycleService service(UUID matchId) {
        return new MatchLifecycleService(fixedClock(), () -> matchId);
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneOffset.UTC);
    }

    private static MatchStartRequest request() {
        return new MatchStartRequest(
                "map-a",
                "solo",
                "test",
                null,
                Set.of(PLAYER_ID),
                Instant.parse("2026-07-10T00:00:00Z")
        );
    }
}
