package com.makar.tacticaltablet.game.lifecycle.integration;

import com.makar.tacticaltablet.game.lifecycle.MatchLifecycleService;
import com.makar.tacticaltablet.game.lifecycle.MatchLifecycleSnapshot;
import com.makar.tacticaltablet.game.lifecycle.MatchStartRequest;
import com.makar.tacticaltablet.game.lifecycle.MatchStartStep;
import com.makar.tacticaltablet.game.lifecycle.MatchState;

import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchStartCoordinatorTest {
    private static final UUID MATCH_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID NEXT_MATCH_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test
    void rejectedPreflightDoesNotCreateContextOrApplySteps() {
        MatchLifecycleService lifecycle = lifecycle(MATCH_ID);
        FakeGateway gateway = new FakeGateway();
        gateway.preflightResult = MatchStartPreflightResult.rejected(
                MatchStartRejectionReason.INSUFFICIENT_PLAYERS,
                "not enough players"
        );
        MatchStartCoordinator coordinator = new MatchStartCoordinator(lifecycle, gateway);

        MatchStartResult result = coordinator.start(null);

        assertEquals(MatchStartStatus.REJECTED, result.status());
        assertEquals(MatchStartRejectionReason.INSUFFICIENT_PLAYERS, result.rejectionReason());
        assertEquals(MatchState.IDLE, lifecycle.snapshot().state());
        assertEquals(0L, lifecycle.snapshot().revision());
        assertTrue(gateway.applied.isEmpty());
    }

    @Test
    void successfulStartAppliesStepsInOrderAndCommitsRunningAfterLastStep() {
        MatchLifecycleService lifecycle = lifecycle(MATCH_ID);
        FakeGateway gateway = new FakeGateway();
        AtomicReference<MatchStartCoordinator> coordinatorRef = new AtomicReference<>();
        gateway.onApply = ignored -> {
            if (gateway.applied.isEmpty()) {
                gateway.firstApplyState = coordinatorRef.get().snapshot().state();
            }
        };
        gateway.onPostCommit = () -> gateway.postCommitState = coordinatorRef.get().snapshot().state();
        MatchStartCoordinator coordinator = new MatchStartCoordinator(lifecycle, gateway);
        coordinatorRef.set(coordinator);

        MatchStartResult result = coordinator.start(null);

        assertEquals(MatchStartStatus.STARTED, result.status());
        assertEquals(MatchState.STARTING, gateway.firstApplyState);
        assertEquals(MatchState.RUNNING, gateway.postCommitState);
        assertEquals(MatchState.RUNNING, lifecycle.snapshot().state());
        assertEquals(MatchStartCoordinator.startSteps(), gateway.applied);
        assertEquals(MatchStartCoordinator.startSteps().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()),
                lifecycle.snapshot().completedLifecycleSteps());
        assertTrue(gateway.rolledBack.isEmpty());
    }

    @Test
    void duplicateStartDoesNotApplySideEffectsAgain() {
        MatchLifecycleService lifecycle = lifecycle(MATCH_ID);
        FakeGateway gateway = new FakeGateway();
        MatchStartCoordinator coordinator = new MatchStartCoordinator(lifecycle, gateway);

        assertEquals(MatchStartStatus.STARTED, coordinator.start(null).status());
        gateway.applied.clear();
        int postCommitCalls = gateway.postCommitCalls;

        MatchStartResult duplicate = coordinator.start(null);

        assertEquals(MatchStartStatus.ALREADY_RUNNING, duplicate.status());
        assertTrue(gateway.applied.isEmpty());
        assertEquals(postCommitCalls, gateway.postCommitCalls);
        assertEquals(MatchState.RUNNING, lifecycle.snapshot().state());
    }

    @Test
    void failureAtEachStepRollsBackCompletedStepsInReverseOrder() {
        for (MatchStartStep failedStep : MatchStartCoordinator.startSteps()) {
            MatchLifecycleService lifecycle = lifecycle(MATCH_ID);
            FakeGateway gateway = new FakeGateway();
            gateway.failApplyAt = failedStep;
            MatchStartCoordinator coordinator = new MatchStartCoordinator(lifecycle, gateway);

            MatchStartResult result = coordinator.start(null);

            List<MatchStartStep> expectedAppliedBeforeFailure = stepsBefore(failedStep);
            List<MatchStartStep> expectedRollback = new ArrayList<>(expectedAppliedBeforeFailure);
            java.util.Collections.reverse(expectedRollback);
            expectedRollback.add(0, failedStep);
            assertEquals(MatchStartStatus.FAILED_ROLLED_BACK, result.status(), failedStep.name());
            assertEquals(MatchState.IDLE, lifecycle.snapshot().state(), failedStep.name());
            assertEquals(failedStep, result.failedStep().orElseThrow(), failedStep.name());
            assertEquals(expectedAppliedBeforeFailure, gateway.applied, failedStep.name());
            assertEquals(expectedRollback, gateway.rolledBack, failedStep.name());
        }
    }

    @Test
    void rollbackFailureLeavesLifecycleFailedAndBlocksNextStart() {
        MatchLifecycleService lifecycle = lifecycle(MATCH_ID);
        FakeGateway gateway = new FakeGateway();
        gateway.failApplyAt = MatchStartStep.START_ZONE;
        gateway.failRollbackAt = MatchStartStep.ENFORCE_GAME_RULES;
        MatchStartCoordinator coordinator = new MatchStartCoordinator(lifecycle, gateway);

        MatchStartResult failed = coordinator.start(null);
        MatchStartResult second = coordinator.start(null);

        assertEquals(MatchStartStatus.FAILED_REQUIRES_CLEANUP, failed.status());
        assertEquals(MatchState.FAILED, lifecycle.snapshot().state());
        assertFalse(failed.rollbackFailures().isEmpty());
        assertEquals(MatchStartStatus.BLOCKED_REQUIRES_CLEANUP, second.status());
        assertEquals(MatchState.FAILED, lifecycle.snapshot().state());
    }

    @Test
    void successfulRollbackAllowsNewStartWithNewMatchId() {
        MatchLifecycleService lifecycle = lifecycle(MATCH_ID, NEXT_MATCH_ID);
        FakeGateway gateway = new FakeGateway();
        gateway.failApplyAt = MatchStartStep.START_ZONE;
        MatchStartCoordinator coordinator = new MatchStartCoordinator(lifecycle, gateway);

        MatchStartResult failed = coordinator.start(null);
        gateway.failApplyAt = null;
        gateway.applied.clear();
        gateway.rolledBack.clear();
        MatchStartResult second = coordinator.start(null);

        assertEquals(MatchStartStatus.FAILED_ROLLED_BACK, failed.status());
        assertEquals(MatchStartStatus.STARTED, second.status());
        assertEquals(NEXT_MATCH_ID, second.matchId().orElseThrow());
        assertEquals(MatchState.RUNNING, lifecycle.snapshot().state());
    }

    @Test
    void postCommitFailureIsWarningAndDoesNotRollbackRunningMatch() {
        MatchLifecycleService lifecycle = lifecycle(MATCH_ID);
        FakeGateway gateway = new FakeGateway();
        gateway.failPostCommit = true;
        MatchStartCoordinator coordinator = new MatchStartCoordinator(lifecycle, gateway);

        MatchStartResult result = coordinator.start(null);

        assertEquals(MatchStartStatus.STARTED, result.status());
        assertEquals(MatchState.RUNNING, lifecycle.snapshot().state());
        assertFalse(result.warnings().isEmpty());
        assertTrue(gateway.rolledBack.isEmpty());
    }

    @Test
    void cleanupStateBlocksStartWithCleanupStatus() {
        MatchLifecycleService lifecycle = lifecycle(MATCH_ID);
        FakeGateway gateway = new FakeGateway();
        MatchStartCoordinator coordinator = new MatchStartCoordinator(lifecycle, gateway);

        assertEquals(MatchStartStatus.STARTED, coordinator.start(null).status());
        lifecycle.beginCleanup(MATCH_ID);
        gateway.applied.clear();

        MatchStartResult result = coordinator.start(null);

        assertEquals(MatchStartStatus.BLOCKED_REQUIRES_CLEANUP, result.status());
        assertTrue(gateway.applied.isEmpty());
    }

    private static MatchLifecycleService lifecycle(UUID... ids) {
        List<UUID> sequence = List.of(ids);
        java.util.concurrent.atomic.AtomicInteger index = new java.util.concurrent.atomic.AtomicInteger();
        return new MatchLifecycleService(
                Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneOffset.UTC),
                () -> sequence.get(Math.min(index.getAndIncrement(), sequence.size() - 1))
        );
    }

    private static List<MatchStartStep> stepsBefore(MatchStartStep step) {
        List<MatchStartStep> result = new ArrayList<>();
        for (MatchStartStep candidate : MatchStartCoordinator.startSteps()) {
            if (candidate == step) {
                return result;
            }
            result.add(candidate);
        }
        return result;
    }

    private static final class FakeGateway implements MatchStartGateway {
        private MatchStartPreflightResult preflightResult = MatchStartPreflightResult.acceptedResult();
        private final List<MatchStartStep> applied = new ArrayList<>();
        private final List<MatchStartStep> rolledBack = new ArrayList<>();
        private MatchStartStep failApplyAt;
        private MatchStartStep failRollbackAt;
        private boolean failPostCommit;
        private int postCommitCalls;
        private java.util.function.Consumer<MatchStartStep> onApply = ignored -> {
        };
        private Runnable onPostCommit = () -> {
        };
        private MatchState firstApplyState;
        private MatchState postCommitState;

        @Override
        public MatchStartPreflightResult preflight(MinecraftServer server, MatchLifecycleSnapshot lifecycleSnapshot) {
            return preflightResult;
        }

        @Override
        public MatchStartRequest createRequest(MinecraftServer server) {
            return new MatchStartRequest(
                    "test-map",
                    "SOLO",
                    "unit-test",
                    null,
                    Set.of(UUID.fromString("30000000-0000-0000-0000-000000000001")),
                    Instant.parse("2026-07-10T00:00:00Z")
            );
        }

        @Override
        public void apply(MinecraftServer server, MatchStartStep step) throws Exception {
            onApply.accept(step);
            if (step == failApplyAt) {
                throw new IllegalStateException("apply failed at " + step);
            }
            applied.add(step);
        }

        @Override
        public void rollback(MinecraftServer server, MatchStartStep step) throws Exception {
            rolledBack.add(step);
            if (step == failRollbackAt) {
                throw new IllegalStateException("rollback failed at " + step);
            }
        }

        @Override
        public void postCommit(MinecraftServer server) throws Exception {
            postCommitCalls++;
            onPostCommit.run();
            if (failPostCommit) {
                throw new IllegalStateException("post commit failed");
            }
        }
    }
}
