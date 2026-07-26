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
    void newPlayerWithinTenMinutesIsRegisteredAsActiveParticipant() {
        Fixture fixture = new Fixture();

        MatchAdmissionService.Admission result = fixture.finalizeAdmission();

        assertEquals(MatchAdmissionOutcome.ACTIVE_PARTICIPANT, result.outcome());
        assertTrue(result.registrationSucceeded());
        assertEquals(1, fixture.registrations.get());
        assertTrue(fixture.participants.get().contains(fixture.playerId));
    }

    @Test
    void deadlinePassingBeforeFinalCheckProducesLateSpectator() {
        Fixture fixture = new Fixture();
        MatchAdmissionService service = fixture.service(fixture::advanceToDeadline);

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
    void registrationRejectionIsReclassifiedAgainstDeadline() {
        Fixture fixture = new Fixture();
        MatchAdmissionService service = fixture.service(
                () -> {
                },
                (matchId, playerId) -> {
                    fixture.registrations.incrementAndGet();
                    fixture.advanceToDeadline();
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
    void participantReconnectAfterTenMinutesRemainsReturningParticipant() {
        Fixture fixture = new Fixture();
        fixture.advanceToDeadline();
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
        fixture.advanceToDeadline();
        MatchAdmissionService.Admission second = fixture.finalizeAdmission();

        assertEquals(MatchAdmissionOutcome.ACTIVE_PARTICIPANT, first.outcome());
        assertEquals(MatchAdmissionOutcome.RETURNING_PARTICIPANT, second.outcome());
        assertEquals(1, fixture.registrations.get());
    }

    @Test
    void lateSpectatorNeverEntersParticipantRegistry() {
        Fixture fixture = new Fixture();
        fixture.advanceToDeadline();

        MatchAdmissionService.Admission first = fixture.finalizeAdmission();
        MatchAdmissionService.Admission repeated = fixture.finalizeAdmission();

        assertEquals(MatchAdmissionOutcome.LATE_SPECTATOR, first.outcome());
        assertEquals(MatchAdmissionOutcome.LATE_SPECTATOR, repeated.outcome());
        assertTrue(fixture.participants.get().isEmpty());
        assertEquals(0, fixture.registrations.get());
    }

    @Test
    void endedMatchRemovesLateJoinRestriction() {
        Fixture fixture = new Fixture();
        fixture.advanceToDeadline();
        fixture.state.set(MatchState.ENDING);

        MatchAdmissionService.Admission result = fixture.finalizeAdmission();

        assertEquals(MatchAdmissionOutcome.NORMAL_LOBBY_PLAYER, result.outcome());
        assertEquals(0, fixture.registrations.get());
    }

    @Test
    void windowForDifferentMatchCannotAdmitPlayer() {
        Fixture fixture = new Fixture();
        fixture.window.open(UUID.randomUUID(), Fixture.START_TICK);

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
        private static final long START_TICK = 1_000L;

        private final UUID matchId = UUID.randomUUID();
        private final UUID playerId = UUID.randomUUID();
        private final AtomicReference<MatchState> state = new AtomicReference<>(MatchState.RUNNING);
        private final MatchAdmissionWindow window = new MatchAdmissionWindow();
        private final AtomicReference<Set<UUID>> participants = new AtomicReference<>(Set.of());
        private final AtomicInteger registrations = new AtomicInteger();
        private final AtomicBoolean disconnected = new AtomicBoolean();

        private Fixture() {
            window.open(matchId, START_TICK);
        }

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
                    window::snapshot,
                    registrar,
                    beforeFinalCheck
            );
        }

        private MatchAdmissionService.Admission finalizeAdmission() {
            return service().finalizeAdmission(playerId, disconnected::get);
        }

        private void advanceToDeadline() {
            window.advance(START_TICK
                    + 600L * 20L);
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
