package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.game.lifecycle.MatchLifecycleSnapshot;
import com.makar.tacticaltablet.game.lifecycle.MatchState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchAdmissionServiceTest {
    @Test
    void inspectionNeverRegistersParticipant() {
        UUID matchId = UUID.randomUUID();
        AtomicInteger registrations = new AtomicInteger();
        MatchAdmissionService service = service(matchId, Set.of(), 2, registrations, true);

        assertEquals(MatchAdmissionStatus.ADMITTED, service.inspect(UUID.randomUUID()).status());
        assertEquals(MatchAdmissionStatus.ADMITTED, service.inspect(UUID.randomUUID()).status());
        assertEquals(0, registrations.get());
    }

    @Test
    void explicitAdmissionRegistersEligiblePlayerOnce() {
        UUID matchId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        AtomicInteger registrations = new AtomicInteger();
        AtomicReference<Set<UUID>> participants = new AtomicReference<>(Set.of());
        MatchAdmissionService service = new MatchAdmissionService(
                () -> snapshot(matchId, participants.get()),
                () -> 1,
                (registeredMatch, registeredPlayer) -> {
                    registrations.incrementAndGet();
                    assertEquals(matchId, registeredMatch);
                    participants.set(Set.of(registeredPlayer));
                    return true;
                }
        );

        MatchAdmissionService.Inspection result = service.admit(playerId);
        assertEquals(MatchAdmissionStatus.ADMITTED, result.status());
        assertTrue(result.alreadyParticipant());
        assertEquals(1, registrations.get());
        assertTrue(service.inspect(playerId).alreadyParticipant());
    }

    @Test
    void phaseThreeInspectionAndAdmissionRemainReadOnly() {
        AtomicInteger registrations = new AtomicInteger();
        MatchAdmissionService service = service(
                UUID.randomUUID(), Set.of(), 3, registrations, true);

        assertEquals(MatchAdmissionStatus.LATE_SPECTATOR, service.inspect(UUID.randomUUID()).status());
        assertEquals(MatchAdmissionStatus.LATE_SPECTATOR, service.admit(UUID.randomUUID()).status());
        assertEquals(0, registrations.get());
    }

    @Test
    void failedRegistrationDoesNotPretendParticipantWasAdmitted() {
        AtomicInteger registrations = new AtomicInteger();
        MatchAdmissionService service = service(
                UUID.randomUUID(), Set.of(), 2, registrations, false);

        MatchAdmissionService.Inspection result = service.admit(UUID.randomUUID());
        assertEquals(MatchAdmissionStatus.LATE_SPECTATOR, result.status());
        assertFalse(result.alreadyParticipant());
        assertEquals(1, registrations.get());
    }

    private static MatchAdmissionService service(
            UUID matchId,
            Set<UUID> participants,
            int phase,
            AtomicInteger registrations,
            boolean registrationResult
    ) {
        return new MatchAdmissionService(
                () -> snapshot(matchId, participants),
                () -> phase,
                (ignoredMatch, ignoredPlayer) -> {
                    registrations.incrementAndGet();
                    return registrationResult;
                }
        );
    }

    private static MatchLifecycleSnapshot snapshot(UUID matchId, Set<UUID> participants) {
        return new MatchLifecycleSnapshot(
                MatchState.RUNNING,
                Optional.of(matchId),
                Optional.of("map"),
                Optional.of("mode"),
                Optional.empty(),
                participants,
                Set.of(),
                2,
                Optional.of(Instant.EPOCH),
                Optional.of(Instant.EPOCH)
        );
    }
}
