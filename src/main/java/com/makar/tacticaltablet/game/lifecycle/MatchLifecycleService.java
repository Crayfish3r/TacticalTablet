package com.makar.tacticaltablet.game.lifecycle;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class MatchLifecycleService {
    private final Clock clock;
    private final Supplier<UUID> matchIdSupplier;
    private MatchContext currentContext;
    private long revision;

    public MatchLifecycleService() {
        this(Clock.systemUTC(), UUID::randomUUID);
    }

    public MatchLifecycleService(Clock clock, Supplier<UUID> matchIdSupplier) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.matchIdSupplier = Objects.requireNonNull(matchIdSupplier, "matchIdSupplier");
    }

    public MatchLifecycleSnapshot snapshot() {
        if (currentContext == null) {
            return MatchLifecycleSnapshot.idle(revision);
        }
        return MatchLifecycleSnapshot.from(currentContext);
    }

    public MatchTransitionResult beginPreparation(MatchStartRequest request) {
        Objects.requireNonNull(request, "request");
        if (currentContext != null) {
            return MatchTransitionResult.noOp(snapshot(), "match already has an active context");
        }

        long nextRevision = revision + 1L;
        currentContext = MatchContext.prepared(matchIdSupplier.get(), request, now(), nextRevision);
        revision = nextRevision;
        return MatchTransitionResult.applied(MatchState.IDLE, currentContext, "match preparation started");
    }

    public MatchTransitionResult markStarting(UUID expectedMatchId) {
        return transition(expectedMatchId, MatchState.STARTING, "start side effects are beginning");
    }

    public MatchTransitionResult markRunning(UUID expectedMatchId) {
        return transition(expectedMatchId, MatchState.RUNNING, "match is running");
    }

    public MatchTransitionResult beginEnding(UUID expectedMatchId, MatchEndReason reason) {
        Objects.requireNonNull(reason, "reason");
        return transition(expectedMatchId, MatchState.ENDING, "match ending: " + reason);
    }

    public MatchTransitionResult beginCleanup(UUID expectedMatchId) {
        return transition(expectedMatchId, MatchState.CLEANING, "match cleanup started");
    }

    public MatchTransitionResult markIdle(UUID expectedMatchId) {
        MatchTransitionResult stale = rejectIfStale(expectedMatchId);
        if (stale != null) return stale;

        if (currentContext == null) {
            return MatchTransitionResult.noOp(snapshot(), "already idle");
        }

        MatchState previous = currentContext.state();
        if (!MatchTransitionPolicy.canTransition(previous, MatchState.IDLE)) {
            return MatchTransitionResult.rejected(snapshot(), "transition " + previous + " -> IDLE is not allowed");
        }

        long nextRevision = revision + 1L;
        revision = nextRevision;
        UUID matchId = currentContext.matchId();
        currentContext = null;
        return new MatchTransitionResult(
                MatchTransitionStatus.APPLIED,
                previous,
                MatchState.IDLE,
                Optional.of(matchId),
                revision,
                Optional.of("match lifecycle is idle"),
                Optional.empty()
        );
    }

    public MatchTransitionResult markFailed(UUID expectedMatchId, MatchFailure failure) {
        Objects.requireNonNull(failure, "failure");
        MatchTransitionResult stale = rejectIfStale(expectedMatchId);
        if (stale != null) return stale;

        if (currentContext == null) {
            return MatchTransitionResult.rejected(snapshot(), "no active match context");
        }

        MatchState previous = currentContext.state();
        if (previous == MatchState.FAILED) {
            return MatchTransitionResult.noOp(snapshot(), "already failed");
        }
        if (!MatchTransitionPolicy.canTransition(previous, MatchState.FAILED)) {
            return MatchTransitionResult.rejected(snapshot(), "transition " + previous + " -> FAILED is not allowed");
        }

        long nextRevision = revision + 1L;
        currentContext = currentContext.withFailure(MatchState.FAILED, failure, now(), nextRevision);
        revision = nextRevision;
        return MatchTransitionResult.applied(previous, currentContext, "match lifecycle failed");
    }

    public MatchTransitionResult recordCompletedStep(UUID expectedMatchId, String step) {
        MatchTransitionResult stale = rejectIfStale(expectedMatchId);
        if (stale != null) return stale;
        if (currentContext == null) {
            return MatchTransitionResult.rejected(snapshot(), "no active match context");
        }
        if (step == null || step.isBlank() || currentContext.completedLifecycleSteps().contains(step.trim())) {
            return MatchTransitionResult.noOp(snapshot(), "step already recorded or blank");
        }

        MatchState previous = currentContext.state();
        long nextRevision = revision + 1L;
        currentContext = currentContext.withCompletedStep(step, nextRevision);
        revision = currentContext.revision();
        return MatchTransitionResult.applied(previous, currentContext, "recorded step " + step.trim());
    }

    private MatchTransitionResult transition(UUID expectedMatchId, MatchState nextState, String diagnostic) {
        MatchTransitionResult stale = rejectIfStale(expectedMatchId);
        if (stale != null) return stale;

        if (currentContext == null) {
            if (nextState == MatchState.ENDING || nextState == MatchState.CLEANING) {
                return MatchTransitionResult.noOp(snapshot(), "no active match context");
            }
            return MatchTransitionResult.rejected(snapshot(), "no active match context");
        }

        MatchState previous = currentContext.state();
        if (previous == nextState) {
            return MatchTransitionResult.noOp(snapshot(), "already " + nextState);
        }
        if (!MatchTransitionPolicy.canTransition(previous, nextState)) {
            return MatchTransitionResult.rejected(snapshot(), "transition " + previous + " -> " + nextState + " is not allowed");
        }

        long nextRevision = revision + 1L;
        currentContext = currentContext.transitionTo(nextState, now(), nextRevision);
        revision = nextRevision;
        return MatchTransitionResult.applied(previous, currentContext, diagnostic);
    }

    private MatchTransitionResult rejectIfStale(UUID expectedMatchId) {
        if (expectedMatchId == null) return null;
        if (currentContext == null) {
            return MatchTransitionResult.rejected(snapshot(), "stale match id " + expectedMatchId + ": no active context");
        }
        if (!currentContext.matchId().equals(expectedMatchId)) {
            return MatchTransitionResult.rejected(snapshot(), "stale match id " + expectedMatchId);
        }
        return null;
    }

    private Instant now() {
        return clock.instant();
    }
}
