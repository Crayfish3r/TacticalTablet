package com.makar.tacticaltablet.game.lifecycle.integration;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.lifecycle.MatchFailure;
import com.makar.tacticaltablet.game.lifecycle.MatchFailureStage;
import com.makar.tacticaltablet.game.lifecycle.MatchLifecycleService;
import com.makar.tacticaltablet.game.lifecycle.MatchLifecycleSnapshot;
import com.makar.tacticaltablet.game.lifecycle.MatchStartRequest;
import com.makar.tacticaltablet.game.lifecycle.MatchStartStep;
import com.makar.tacticaltablet.game.lifecycle.MatchState;
import com.makar.tacticaltablet.game.lifecycle.MatchTransitionResult;
import com.makar.tacticaltablet.game.lifecycle.MatchTransitionStatus;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MatchStartCoordinator {
    private static final List<MatchStartStep> START_STEPS = List.of(
            MatchStartStep.RESET_TRANSIENT_RUNTIME,
            MatchStartStep.CONFIGURE_TEAMS,
            MatchStartStep.RESET_AIRDROP_SCHEDULER,
            MatchStartStep.START_DISCORD_TRACKING,
            MatchStartStep.START_CONTRACTS,
            MatchStartStep.RESET_MATCH_COUNTERS,
            MatchStartStep.EXECUTE_START_DATAPACK,
            MatchStartStep.ANNOUNCE_MAP_START,
            MatchStartStep.ENFORCE_GAME_RULES,
            MatchStartStep.START_ZONE,
            MatchStartStep.PREPARE_SAFE_TELEPORT,
            MatchStartStep.INITIALIZE_PLAYERS,
            MatchStartStep.START_VOICE_MATCH,
            MatchStartStep.CAPTURE_PARTICIPANTS,
            MatchStartStep.START_EXTRACTION,
            MatchStartStep.SYNC_CLASS_XP,
            MatchStartStep.SET_LEGACY_RUNNING
    );

    private final MatchLifecycleService lifecycleService;
    private final MatchStartGateway gateway;

    public MatchStartCoordinator(MatchLifecycleService lifecycleService, MatchStartGateway gateway) {
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    public MatchStartResult start(MinecraftServer server) {
        MatchLifecycleSnapshot initialSnapshot = lifecycleService.snapshot();
        MatchStartPreflightResult preflight = gateway.preflight(server, initialSnapshot);
        if (!preflight.accepted()) {
            return rejectedResult(initialSnapshot, preflight);
        }

        MatchStartRequest request = gateway.createRequest(server);
        MatchTransitionResult prepared = lifecycleService.beginPreparation(request);
        if (prepared.status() != MatchTransitionStatus.APPLIED) {
            return rejectedResult(initialSnapshot, MatchStartPreflightResult.rejected(
                    MatchStartRejectionReason.LIFECYCLE_NOT_IDLE,
                    prepared.diagnostic().orElse("lifecycle did not accept start preparation")
            ));
        }

        UUID matchId = prepared.matchId().orElseThrow();
        MatchTransitionResult starting = lifecycleService.markStarting(matchId);
        if (!transitionReached(starting, MatchState.STARTING)) {
            List<String> cleanupFailures = new ArrayList<>();
            MatchTransitionResult failed = lifecycleService.markFailed(matchId, MatchFailure.of(
                    MatchFailureStage.PREPARATION,
                    starting.diagnostic().orElse("failed to enter STARTING")
            ));
            collectUnexpectedTransition(cleanupFailures, "markFailed", failed, MatchState.FAILED);
            cleanupFailures.addAll(transitionToIdleAfterRollback(matchId));
            return new MatchStartResult(
                    cleanupFailures.isEmpty()
                            ? MatchStartStatus.FAILED_ROLLED_BACK
                            : MatchStartStatus.FAILED_REQUIRES_CLEANUP,
                    Optional.of(matchId),
                    initialSnapshot.state(),
                    lifecycleService.snapshot().state(),
                    Optional.empty(),
                    MatchStartRejectionReason.UNKNOWN,
                    transitionDiagnostic("markStarting", starting),
                    List.of(),
                    cleanupFailures
            );
        }

        List<MatchStartStep> completed = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            for (MatchStartStep step : START_STEPS) {
                gateway.apply(server, step);
                MatchTransitionResult recorded = lifecycleService.recordCompletedStep(matchId, step.name());
                if (recorded.status() != MatchTransitionStatus.APPLIED
                        || recorded.currentState() != MatchState.STARTING) {
                    throw new MatchStartFailure(step, transitionDiagnostic("recordCompletedStep " + step.name(), recorded));
                }
                completed.add(step);
            }

            MatchTransitionResult running = lifecycleService.markRunning(matchId);
            if (!transitionReached(running, MatchState.RUNNING)
                    || running.status() != MatchTransitionStatus.APPLIED) {
                throw new MatchStartFailure(null, transitionDiagnostic("markRunning", running));
            }

            try {
                gateway.postCommit(server);
            } catch (Exception exception) {
                String warning = diagnostic("postCommit", exception);
                warnings.add(warning);
                TacticalTabletMod.LOGGER.warn("Match start post-commit warning matchId={}: {}", matchId, warning, exception);
            }

            return new MatchStartResult(
                    MatchStartStatus.STARTED,
                    Optional.of(matchId),
                    initialSnapshot.state(),
                    lifecycleService.snapshot().state(),
                    Optional.empty(),
                    MatchStartRejectionReason.NONE,
                    "match started",
                    warnings,
                    List.of()
            );
        } catch (Exception exception) {
            MatchStartStep failedStep = exception instanceof MatchStartFailure startFailure
                    ? startFailure.step()
                    : nextFailedStep(completed);
            String failureDiagnostic = diagnostic(failedStep == null ? "markRunning" : failedStep.name(), exception);
            TacticalTabletMod.LOGGER.error("Match start failed matchId={} step={}: {}", matchId, failedStep, failureDiagnostic, exception);
            List<String> lifecycleFailures = new ArrayList<>();
            MatchTransitionResult failed = lifecycleService.markFailed(matchId, MatchFailure.from(MatchFailureStage.STARTING, exception));
            collectUnexpectedTransition(lifecycleFailures, "markFailed", failed, MatchState.FAILED);

            List<String> rollbackFailures = rollback(server, completed, failedStep);
            if (rollbackFailures.isEmpty()) {
                lifecycleFailures.addAll(transitionToIdleAfterRollback(matchId));
            }

            List<String> failures = new ArrayList<>(lifecycleFailures);
            failures.addAll(rollbackFailures);
            if (failures.isEmpty()) {
                return new MatchStartResult(
                        MatchStartStatus.FAILED_ROLLED_BACK,
                        Optional.of(matchId),
                        initialSnapshot.state(),
                        lifecycleService.snapshot().state(),
                        Optional.ofNullable(failedStep),
                        MatchStartRejectionReason.NONE,
                        failureDiagnostic,
                        warnings,
                        failures
                );
            }

            return new MatchStartResult(
                    MatchStartStatus.FAILED_REQUIRES_CLEANUP,
                    Optional.of(matchId),
                    initialSnapshot.state(),
                    lifecycleService.snapshot().state(),
                    Optional.ofNullable(failedStep),
                    MatchStartRejectionReason.NONE,
                    failureDiagnostic,
                    warnings,
                    failures
            );
        }
    }

    public MatchLifecycleSnapshot snapshot() {
        return lifecycleService.snapshot();
    }

    public MatchTransitionResult registerParticipant(UUID matchId, UUID playerId) {
        return lifecycleService.registerParticipant(matchId, playerId);
    }

    public void clearAfterLegacyCleanup() {
        MatchLifecycleSnapshot snapshot = lifecycleService.snapshot();
        if (snapshot.matchId().isEmpty()) {
            return;
        }
        UUID matchId = snapshot.matchId().orElseThrow();
        if (snapshot.state() != MatchState.CLEANING) {
            MatchTransitionResult cleanup = lifecycleService.beginCleanup(matchId);
            if (!transitionReached(cleanup, MatchState.CLEANING)) {
                TacticalTabletMod.LOGGER.warn("Match lifecycle cleanup transition was not applied: {}",
                        transitionDiagnostic("beginCleanup", cleanup));
                return;
            }
        }
        MatchTransitionResult idle = lifecycleService.markIdle(matchId);
        if (!transitionReached(idle, MatchState.IDLE)) {
            TacticalTabletMod.LOGGER.warn("Match lifecycle idle transition was not applied: {}",
                    transitionDiagnostic("markIdle", idle));
        }
    }

    public static List<MatchStartStep> startSteps() {
        return START_STEPS;
    }

    private MatchStartResult rejectedResult(MatchLifecycleSnapshot snapshot, MatchStartPreflightResult preflight) {
        MatchStartStatus status = switch (snapshot.state()) {
            case PREPARING -> MatchStartStatus.ALREADY_STARTING;
            case STARTING -> MatchStartStatus.ALREADY_STARTING;
            case RUNNING, ENDING -> MatchStartStatus.ALREADY_RUNNING;
            case CLEANING, FAILED -> MatchStartStatus.BLOCKED_REQUIRES_CLEANUP;
            case IDLE -> MatchStartStatus.REJECTED;
        };
        return new MatchStartResult(
                status,
                snapshot.matchId(),
                snapshot.state(),
                snapshot.state(),
                Optional.empty(),
                preflight.reason(),
                preflight.diagnostic(),
                List.of(),
                List.of()
        );
    }

    private List<String> rollback(MinecraftServer server, List<MatchStartStep> completed, MatchStartStep failedStep) {
        List<String> failures = new ArrayList<>();
        if (failedStep != null && START_STEPS.contains(failedStep) && !completed.contains(failedStep)) {
            rollbackStep(server, failedStep, failures);
        }
        for (int i = completed.size() - 1; i >= 0; i--) {
            rollbackStep(server, completed.get(i), failures);
        }
        return failures;
    }

    private void rollbackStep(MinecraftServer server, MatchStartStep step, List<String> failures) {
        try {
            gateway.rollback(server, step);
        } catch (Exception rollbackException) {
            String diagnostic = diagnostic(step.name(), rollbackException);
            failures.add(diagnostic);
            TacticalTabletMod.LOGGER.error("Match start rollback failed step={}: {}", step, diagnostic, rollbackException);
        }
    }

    private static MatchStartStep nextFailedStep(List<MatchStartStep> completed) {
        int index = completed.size();
        if (index >= START_STEPS.size()) {
            return null;
        }
        return START_STEPS.get(index);
    }

    private static String diagnostic(String stage, Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        String diagnostic = stage + ": " + exception.getClass().getSimpleName() + ": " + message;
        return diagnostic.length() <= 512 ? diagnostic : diagnostic.substring(0, 512);
    }

    private List<String> transitionToIdleAfterRollback(UUID matchId) {
        List<String> failures = new ArrayList<>();
        MatchTransitionResult cleanup = lifecycleService.beginCleanup(matchId);
        collectUnexpectedTransition(failures, "beginCleanup", cleanup, MatchState.CLEANING);
        if (transitionReached(cleanup, MatchState.CLEANING)) {
            MatchTransitionResult idle = lifecycleService.markIdle(matchId);
            collectUnexpectedTransition(failures, "markIdle", idle, MatchState.IDLE);
        }
        return failures;
    }

    private static void collectUnexpectedTransition(
            List<String> failures,
            String stage,
            MatchTransitionResult result,
            MatchState expectedState
    ) {
        if (!transitionReached(result, expectedState)) {
            failures.add(transitionDiagnostic(stage, result));
        }
    }

    private static boolean transitionReached(MatchTransitionResult result, MatchState expectedState) {
        return result != null
                && (result.status() == MatchTransitionStatus.APPLIED || result.status() == MatchTransitionStatus.NO_OP)
                && result.currentState() == expectedState;
    }

    private static String transitionDiagnostic(String stage, MatchTransitionResult result) {
        if (result == null) {
            return stage + ": missing transition result";
        }
        String diagnostic = result.diagnostic().orElse("no diagnostic");
        String message = stage + ": " + result.status()
                + " " + result.previousState()
                + " -> " + result.currentState()
                + ": " + diagnostic;
        return message.length() <= 512 ? message : message.substring(0, 512);
    }

    private static final class MatchStartFailure extends Exception {
        private final MatchStartStep step;

        private MatchStartFailure(MatchStartStep step, String message) {
            super(message);
            this.step = step;
        }

        private MatchStartStep step() {
            return step;
        }
    }
}
