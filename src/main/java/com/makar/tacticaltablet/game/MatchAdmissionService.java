package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.game.lifecycle.MatchLifecycleSnapshot;
import com.makar.tacticaltablet.game.lifecycle.MatchState;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class MatchAdmissionService {
    private final Supplier<MatchLifecycleSnapshot> snapshotSupplier;
    private final IntSupplier phaseSupplier;
    private final BiPredicate<UUID, UUID> participantRegistrar;

    MatchAdmissionService(
            Supplier<MatchLifecycleSnapshot> snapshotSupplier,
            IntSupplier phaseSupplier,
            BiPredicate<UUID, UUID> participantRegistrar
    ) {
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.phaseSupplier = Objects.requireNonNull(phaseSupplier, "phaseSupplier");
        this.participantRegistrar = Objects.requireNonNull(participantRegistrar, "participantRegistrar");
    }

    Inspection inspect(UUID playerId) {
        if (playerId == null) {
            return new Inspection(MatchAdmissionStatus.NO_ACTIVE_MATCH, null, 0, false);
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
                participant
        );
    }

    Inspection admit(UUID playerId) {
        Inspection inspection = inspect(playerId);
        if (inspection.status() != MatchAdmissionStatus.ADMITTED || inspection.alreadyParticipant()) {
            return inspection;
        }
        if (!participantRegistrar.test(inspection.matchId(), playerId)) {
            return new Inspection(
                    MatchAdmissionStatus.LATE_SPECTATOR,
                    inspection.matchId(),
                    inspection.phase(),
                    false
            );
        }
        return new Inspection(
                MatchAdmissionStatus.ADMITTED,
                inspection.matchId(),
                inspection.phase(),
                true
        );
    }

    record Inspection(
            MatchAdmissionStatus status,
            UUID matchId,
            int phase,
            boolean alreadyParticipant
    ) {
    }
}
