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
        if (starting.status() != MatchTransitionStatus.APPLIED) {
            lifecycleService.markFailed(matchId, MatchFailure.of(
                    MatchFailureStage.PREPARATION,
                    starting.diagnostic().orElse("failed to enter STARTING")
            ));
            lifecycleService.markIdle(matchId);
            return new MatchStartResult(
                    MatchStartStatus.FAILED_ROLLED_BACK,
                    Optional.of(matchId),
                    initialSnapshot.state(),
                    lifecycleService.snapshot().state(),
                    Optional.empty(),
                    MatchStartRejectionReason.UNKNOWN,
                    starting.diagnostic().orElse("failed to enter STARTING"),
                    List.of(),
                    List.of()
            );
        }

        List<MatchStartStep> completed = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            for (MatchStartStep step : START_STEPS) {
                gateway.apply(server, step);
                completed.add(step);
                MatchTransitionResult recorded = lifecycleService.recordCompletedStep(matchId, step.name());
                if (recorded.status() == MatchTransitionStatus.REJECTED || recorded.status() == MatchTransitionStatus.FAILED) {
                    throw new MatchStartFailure(step, recorded.diagnostic().orElse("failed to record completed step"));
                }
            }

            MatchTransitionResult running = lifecycleService.markRunning(matchId);
            if (running.status() != MatchTransitionStatus.APPLIED && running.status() != MatchTransitionStatus.NO_OP) {
                throw new MatchStartFailure(null, running.diagnostic().orElse("failed to enter RUNNING"));
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
            lifecycleService.markFailed(matchId, MatchFailure.from(MatchFailureStage.STARTING, exception));

            List<String> rollbackFailures = rollback(server, completed);
            if (rollbackFailures.isEmpty()) {
                lifecycleService.markIdle(matchId);
                return new MatchStartResult(
                        MatchStartStatus.FAILED_ROLLED_BACK,
                        Optional.of(matchId),
                        initialSnapshot.state(),
                        lifecycleService.snapshot().state(),
                        Optional.ofNullable(failedStep),
                        MatchStartRejectionReason.NONE,
                        failureDiagnostic,
                        warnings,
                        rollbackFailures
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
                    rollbackFailures
            );
        }
    }

    public MatchLifecycleSnapshot snapshot() {
        return lifecycleService.snapshot();
    }

    public void clearAfterLegacyCleanup() {
        MatchLifecycleSnapshot snapshot = lifecycleService.snapshot();
        if (snapshot.matchId().isEmpty()) {
            return;
        }
        UUID matchId = snapshot.matchId().orElseThrow();
        if (snapshot.state() != MatchState.CLEANING) {
            lifecycleService.beginCleanup(matchId);
        }
        lifecycleService.markIdle(matchId);
    }

    public static List<MatchStartStep> startSteps() {
        return START_STEPS;
    }

    private MatchStartResult rejectedResult(MatchLifecycleSnapshot snapshot, MatchStartPreflightResult preflight) {
        MatchStartStatus status = switch (snapshot.state()) {
            case PREPARING -> MatchStartStatus.ALREADY_STARTING;
            case STARTING -> MatchStartStatus.ALREADY_STARTING;
            case RUNNING, ENDING, CLEANING, FAILED -> MatchStartStatus.ALREADY_RUNNING;
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

    private List<String> rollback(MinecraftServer server, List<MatchStartStep> completed) {
        List<String> failures = new ArrayList<>();
        for (int i = completed.size() - 1; i >= 0; i--) {
            MatchStartStep step = completed.get(i);
            try {
                gateway.rollback(server, step);
            } catch (Exception rollbackException) {
                String diagnostic = diagnostic(step.name(), rollbackException);
                failures.add(diagnostic);
                TacticalTabletMod.LOGGER.error("Match start rollback failed step={}: {}", step, diagnostic, rollbackException);
            }
        }
        return failures;
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
