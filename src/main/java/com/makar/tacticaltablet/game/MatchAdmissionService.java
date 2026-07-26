package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.game.lifecycle.MatchLifecycleSnapshot;
import com.makar.tacticaltablet.game.lifecycle.MatchState;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class MatchAdmissionService {
    private static final int MAX_REGISTRATION_ATTEMPTS = 2;

    private final Supplier<MatchLifecycleSnapshot> snapshotSupplier;
    private final IntSupplier phaseSupplier;
    private final BiPredicate<UUID, UUID> participantRegistrar;
    private final Runnable beforeFinalCheck;

    MatchAdmissionService(
            Supplier<MatchLifecycleSnapshot> snapshotSupplier,
            IntSupplier phaseSupplier,
            BiPredicate<UUID, UUID> participantRegistrar
    ) {
        this(snapshotSupplier, phaseSupplier, participantRegistrar, () -> {
        });
    }

    MatchAdmissionService(
            Supplier<MatchLifecycleSnapshot> snapshotSupplier,
            IntSupplier phaseSupplier,
            BiPredicate<UUID, UUID> participantRegistrar,
            Runnable beforeFinalCheck
    ) {
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.phaseSupplier = Objects.requireNonNull(phaseSupplier, "phaseSupplier");
        this.participantRegistrar = Objects.requireNonNull(participantRegistrar, "participantRegistrar");
        this.beforeFinalCheck = Objects.requireNonNull(beforeFinalCheck, "beforeFinalCheck");
    }

    Inspection inspect(UUID playerId) {
        if (playerId == null) {
            return inactiveInspection();
        }
        MatchLifecycleSnapshot snapshot = snapshotSupplier.get();
        UUID matchId = snapshot.matchId().orElse(null);
        int phase = phaseSupplier.getAsInt();
        boolean active = matchId != null
                && (snapshot.state() == MatchState.STARTING || snapshot.state() == MatchState.RUNNING);
        boolean participant = snapshot.participantIds().contains(playerId);
        return new Inspection(
                MatchAdmissionPolicy.classify(active, participant, phase),
                matchId,
                phase,
                participant,
                snapshot.state(),
                snapshot.revision()
        );
    }

    Admission finalizeAdmission(UUID playerId, BooleanSupplier disconnected) {
        Objects.requireNonNull(disconnected, "disconnected");
        Inspection initial = inspect(playerId);
        if (playerId == null || disconnected.getAsBoolean()) {
            return Admission.disconnected(initial);
        }
        Admission terminal = terminalWithoutRegistration(initial, initial, false);
        if (terminal != null) {
            return terminal;
        }

        beforeFinalCheck.run();
        if (disconnected.getAsBoolean()) {
            return Admission.disconnected(initial);
        }

        Inspection current = inspect(playerId);
        terminal = terminalWithoutRegistration(initial, current, false);
        if (terminal != null) {
            return terminal;
        }

        for (int attempt = 0; attempt < MAX_REGISTRATION_ATTEMPTS; attempt++) {
            boolean registered;
            try {
                registered = participantRegistrar.test(current.matchId(), playerId);
            } catch (RuntimeException exception) {
                Inspection failed = inspect(playerId);
                return Admission.internalFailure(initial, failed, exception.getClass().getSimpleName());
            }

            if (disconnected.getAsBoolean()) {
                return Admission.disconnected(initial);
            }

            Inspection committed = inspect(playerId);
            if (committed.alreadyParticipant()
                    && Objects.equals(current.matchId(), committed.matchId())) {
                return new Admission(
                        MatchAdmissionOutcome.ACTIVE_PARTICIPANT,
                        initial,
                        committed,
                        true,
                        registered,
                        false,
                        ""
                );
            }

            terminal = terminalWithoutRegistration(initial, committed, true);
            if (terminal != null) {
                return terminal;
            }
            current = committed;
        }

        return Admission.internalFailure(
                initial,
                current,
                "participant registrar rejected an unchanged eligible match"
        );
    }

    private static Admission terminalWithoutRegistration(
            Inspection initial,
            Inspection current,
            boolean registrationAttempted
    ) {
        if (current.alreadyParticipant()) {
            MatchAdmissionOutcome outcome = initial.alreadyParticipant()
                    ? MatchAdmissionOutcome.RETURNING_PARTICIPANT
                    : MatchAdmissionOutcome.ACTIVE_PARTICIPANT;
            return new Admission(
                    outcome,
                    initial,
                    current,
                    registrationAttempted,
                    false,
                    false,
                    ""
            );
        }
        return switch (current.status()) {
            case NO_ACTIVE_MATCH -> new Admission(
                    MatchAdmissionOutcome.NORMAL_LOBBY_PLAYER,
                    initial,
                    current,
                    registrationAttempted,
                    false,
                    false,
                    ""
            );
            case LATE_SPECTATOR -> new Admission(
                    MatchAdmissionOutcome.LATE_SPECTATOR,
                    initial,
                    current,
                    registrationAttempted,
                    false,
                    false,
                    ""
            );
            case ADMITTED -> null;
        };
    }

    private static Inspection inactiveInspection() {
        return new Inspection(
                MatchAdmissionStatus.NO_ACTIVE_MATCH,
                null,
                0,
                false,
                MatchState.IDLE,
                0L
        );
    }

    record Inspection(
            MatchAdmissionStatus status,
            UUID matchId,
            int phase,
            boolean alreadyParticipant,
            MatchState matchState,
            long revision
    ) {
    }

    record Admission(
            MatchAdmissionOutcome outcome,
            Inspection initial,
            Inspection current,
            boolean registrationAttempted,
            boolean registrationSucceeded,
            boolean internalFailure,
            String diagnostic
    ) {
        private static Admission disconnected(Inspection inspection) {
            return new Admission(
                    MatchAdmissionOutcome.DISCONNECTED,
                    inspection,
                    inspection,
                    false,
                    false,
                    false,
                    ""
            );
        }

        private static Admission internalFailure(
                Inspection initial,
                Inspection current,
                String diagnostic
        ) {
            return new Admission(
                    MatchAdmissionOutcome.LATE_SPECTATOR,
                    initial,
                    current,
                    true,
                    false,
                    true,
                    diagnostic == null ? "unknown admission failure" : diagnostic
            );
        }
    }
}
