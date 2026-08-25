package com.makar.tacticaltablet.game.lifecycle.integration;

import com.makar.tacticaltablet.game.lifecycle.MatchLifecycleService;
import com.makar.tacticaltablet.game.lifecycle.MatchLifecycleSnapshot;
import com.makar.tacticaltablet.game.lifecycle.MatchStartRequest;
import com.makar.tacticaltablet.game.lifecycle.MatchStartStep;
import com.makar.tacticaltablet.game.lifecycle.MatchState;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchCleanupIdempotencyTest {
    @Test
    void repeatedCleanupIsSafeAndAllowsAnotherStart() {
        MatchStartCoordinator coordinator = new MatchStartCoordinator(
                new MatchLifecycleService(),
                new NoOpGateway()
        );

        assertEquals(MatchStartStatus.STARTED, coordinator.start(null).status());
        assertEquals(MatchState.RUNNING, coordinator.snapshot().state());

        coordinator.clearAfterLegacyCleanup();
        coordinator.clearAfterLegacyCleanup();
        assertEquals(MatchState.IDLE, coordinator.snapshot().state());

        assertEquals(MatchStartStatus.STARTED, coordinator.start(null).status());
        assertEquals(MatchState.RUNNING, coordinator.snapshot().state());
    }

    private static final class NoOpGateway implements MatchStartGateway {
        @Override
        public MatchStartPreflightResult preflight(
                MinecraftServer server,
                MatchLifecycleSnapshot lifecycleSnapshot
        ) {
            return MatchStartPreflightResult.acceptedResult();
        }

        @Override
        public MatchStartRequest createRequest(MinecraftServer server) {
            return new MatchStartRequest(
                    "test-map",
                    "SOLO",
                    "unit-test",
                    null,
                    Set.of(UUID.fromString("40000000-0000-0000-0000-000000000001")),
                    Instant.parse("2026-08-24T00:00:00Z")
            );
        }

        @Override
        public void apply(MinecraftServer server, MatchStartStep step) {
        }

        @Override
        public void rollback(MinecraftServer server, MatchStartStep step) {
        }

        @Override
        public void postCommit(MinecraftServer server) {
        }
    }
}
