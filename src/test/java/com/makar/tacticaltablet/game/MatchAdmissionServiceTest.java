package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.game.lifecycle.MatchLifecycleSnapshot;
import com.makar.tacticaltablet.game.lifecycle.MatchState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchAdmissionServiceTest {
    @Test
    void inspectionNeverRegistersParticipant() {
        Fixture fixture = new Fixture();

        assertEquals(MatchAdmissionStatus.ADMITTED, fixture.service().inspect(fixture.playerId).status());
        assertEquals(MatchAdmissionStatus.ADMITTED, fixture.service().inspect(fixture.playerId).status());
        assertEquals(0, fixture.registrations.get());
    }

    @Test
    void newEarlyPlayerIsRegisteredAsActiveParticipant() {
        Fixture fixture = new Fixture();

        MatchAdmissionService.Admission result = fixture.finalizeAdmission();

        assertEquals(MatchAdmissionOutcome.ACTIVE_PARTICIPANT, result.outcome());
        assertTrue(result.registrationSucceeded());
        assertEquals(1, fixture.registrations.get());
        assertTrue(fixture.participants.get().contains(fixture.playerId));
    }

    @Test
    void phaseTurningLateBeforeFinalCheckProducesLateSpectator() {
        Fixture fixture = new Fixture();
        MatchAdmissionService service = fixture.service(() -> fixture.phase.set(3));

        MatchAdmissionService.Admission result =
                service.finalizeAdmission(fixture.playerId, fixture.disconnected::get);

        assertEquals(MatchAdmissionOutcome.LATE_SPECTATOR, result.outcome());
        assertEquals(0, fixture.registrations.get());
        assertFalse(fixture.participants.get().contains(fixture.playerId));
    }

    @Test
    void matchEndingBeforeFinalCheckProducesNormalLobbyPlayer() {
        Fixture fixture = new Fixture();
        MatchAdmissionService service = fixture.service(() -> fixture.state.set(MatchState.ENDING));

        MatchAdmissionService.Admission result =
                service.finalizeAdmission(fixture.playerId, fixture.disconnected::get);

        assertEquals(MatchAdmissionOutcome.NORMAL_LOBBY_PLAYER, result.outcome());
        assertEquals(0, fixture.registrations.get());
    }

    @Test
    void registrationRejectionIsReclassifiedAgainstLatePhase() {
        Fixture fixture = new Fixture();
        MatchAdmissionService service = fixture.service(
                () -> {
                },
                (matchId, playerId) -> {
                    fixture.registrations.incrementAndGet();
                    fixture.phase.set(3);
                    return false;
                }
        );

        MatchAdmissionService.Admission result =
                service.finalizeAdmission(fixture.playerId, fixture.disconnected::get);

        assertEquals(MatchAdmissionOutcome.LATE_SPECTATOR, result.outcome());
        assertFalse(result.internalFailure());
        assertEquals(1, fixture.registrations.get());
    }

    @Test
    void registrationRejectionIsReclassifiedAfterMatchEnds() {
        Fixture fixture = new Fixture();
        MatchAdmissionService service = fixture.service(
                () -> {
                },
                (matchId, playerId) -> {
                    fixture.registrations.incrementAndGet();
                    fixture.state.set(MatchState.ENDING);
                    return false;
                }
        );

        MatchAdmissionService.Admission result =
                service.finalizeAdmission(fixture.playerId, fixture.disconnected::get);

        assertEquals(MatchAdmissionOutcome.NORMAL_LOBBY_PLAYER, result.outcome());
        assertFalse(result.internalFailure());
        assertEquals(1, fixture.registrations.get());
    }

    @Test
    void participantReconnectDuringLatePhaseRemainsReturningParticipant() {
        Fixture fixture = new Fixture();
        fixture.phase.set(4);
        fixture.participants.set(Set.of(fixture.playerId));

        MatchAdmissionService.Admission result = fixture.finalizeAdmission();

        assertEquals(MatchAdmissionOutcome.RETURNING_PARTICIPANT, result.outcome());
        assertEquals(0, fixture.registrations.get());
    }

    @Test
    void disconnectBeforeRegistrationTerminatesWithoutMutation() {
        Fixture fixture = new Fixture();
        MatchAdmissionService service = fixture.service(() -> fixture.disconnected.set(true));

        MatchAdmissionService.Admission result =
                service.finalizeAdmission(fixture.playerId, fixture.disconnected::get);

        assertEquals(MatchAdmissionOutcome.DISCONNECTED, result.outcome());
        assertEquals(0, fixture.registrations.get());
        assertTrue(fixture.participants.get().isEmpty());
    }

    @Test
    void repeatedFinalizationDoesNotRegisterParticipantTwice() {
        Fixture fixture = new Fixture();

        MatchAdmissionService.Admission first = fixture.finalizeAdmission();
        MatchAdmissionService.Admission second = fixture.finalizeAdmission();

        assertEquals(MatchAdmissionOutcome.ACTIVE_PARTICIPANT, first.outcome());
        assertEquals(MatchAdmissionOutcome.RETURNING_PARTICIPANT, second.outcome());
        assertEquals(1, fixture.registrations.get());
    }

    @Test
    void lateSpectatorNeverEntersParticipantRegistry() {
        Fixture fixture = new Fixture();
        fixture.phase.set(3);

        MatchAdmissionService.Admission result = fixture.finalizeAdmission();

        assertEquals(MatchAdmissionOutcome.LATE_SPECTATOR, result.outcome());
        assertTrue(fixture.participants.get().isEmpty());
        assertEquals(0, fixture.registrations.get());
    }

    @Test
    void endedMatchRemovesLateJoinRestriction() {
        Fixture fixture = new Fixture();
        fixture.phase.set(5);
        fixture.state.set(MatchState.ENDING);

        MatchAdmissionService.Admission result = fixture.finalizeAdmission();

        assertEquals(MatchAdmissionOutcome.NORMAL_LOBBY_PLAYER, result.outcome());
        assertEquals(0, fixture.registrations.get());
    }

    @Test
    void unchangedRegistrationFailureUsesSafeTerminalOutcome() {
        Fixture fixture = new Fixture();
        MatchAdmissionService service = fixture.service(
                () -> {
                },
                (matchId, playerId) -> {
                    fixture.registrations.incrementAndGet();
                    return false;
                }
        );

        MatchAdmissionService.Admission result =
                service.finalizeAdmission(fixture.playerId, fixture.disconnected::get);

        assertEquals(MatchAdmissionOutcome.LATE_SPECTATOR, result.outcome());
        assertTrue(result.internalFailure());
        assertEquals(2, fixture.registrations.get());
        assertTrue(fixture.participants.get().isEmpty());
    }

    @Test
    void registrarExceptionUsesSafeTerminalOutcome() {
        Fixture fixture = new Fixture();
        MatchAdmissionService service = fixture.service(
                () -> {
                },
                (matchId, playerId) -> {
                    throw new IllegalStateException("simulated registry failure");
                }
        );

        MatchAdmissionService.Admission result =
                service.finalizeAdmission(fixture.playerId, fixture.disconnected::get);

        assertEquals(MatchAdmissionOutcome.LATE_SPECTATOR, result.outcome());
        assertTrue(result.internalFailure());
        assertEquals("IllegalStateException", result.diagnostic());
        assertTrue(fixture.participants.get().isEmpty());
    }

    private static final class Fixture {
        private final UUID matchId = UUID.randomUUID();
        private final UUID playerId = UUID.randomUUID();
        private final AtomicReference<MatchState> state = new AtomicReference<>(MatchState.RUNNING);
        private final AtomicInteger phase = new AtomicInteger(2);
        private final AtomicReference<Set<UUID>> participants = new AtomicReference<>(Set.of());
        private final AtomicInteger registrations = new AtomicInteger();
        private final AtomicBoolean disconnected = new AtomicBoolean();

        private MatchAdmissionService service() {
            return service(() -> {
            });
        }

        private MatchAdmissionService service(Runnable beforeFinalCheck) {
            return service(beforeFinalCheck, (registeredMatch, registeredPlayer) -> {
                registrations.incrementAndGet();
                if (!matchId.equals(registeredMatch)
                        || (state.get() != MatchState.STARTING && state.get() != MatchState.RUNNING)) {
                    return false;
                }
                participants.updateAndGet(existing -> {
                    LinkedHashSet<UUID> updated = new LinkedHashSet<>(existing);
                    updated.add(registeredPlayer);
                    return Set.copyOf(updated);
                });
                return true;
            });
        }

        private MatchAdmissionService service(
                Runnable beforeFinalCheck,
                java.util.function.BiPredicate<UUID, UUID> registrar
        ) {
            return new MatchAdmissionService(
                    () -> snapshot(matchId, state.get(), participants.get()),
                    phase::get,
                    registrar,
                    beforeFinalCheck
            );
        }

        private MatchAdmissionService.Admission finalizeAdmission() {
            return service().finalizeAdmission(playerId, disconnected::get);
        }
    }

    private static MatchLifecycleSnapshot snapshot(
            UUID matchId,
            MatchState state,
            Set<UUID> participants
    ) {
        return new MatchLifecycleSnapshot(
                state,
                Optional.ofNullable(matchId),
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
