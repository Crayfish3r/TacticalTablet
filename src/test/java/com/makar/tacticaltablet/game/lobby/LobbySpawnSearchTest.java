package com.makar.tacticaltablet.game.lobby;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbySpawnSearchTest {
    private static final LobbySpawnSearch.Position CANONICAL =
            new LobbySpawnSearch.Position(0, 69, 0);
    private static final LobbySpawnSearch.Bounds BOUNDS =
            new LobbySpawnSearch.Bounds(-10, 9, 64, 84, -10, 9);

    @Test
    void solidFloorAndFreeHeadroomIsValid() {
        GridProbe probe = new GridProbe().solid(0, 68, 0);

        assertTrue(LobbySpawnSearch.validate(CANONICAL, probe).valid());
    }

    @Test
    void airUnderPlayerIsInvalid() {
        LobbySpawnSearch.Validation result = LobbySpawnSearch.validate(CANONICAL, new GridProbe());

        assertFalse(result.valid());
        assertEquals(LobbySpawnSearch.Failure.NO_SUPPORT, result.failure());
    }

    @Test
    void solidBlockInsidePlayerBodyIsInvalid() {
        GridProbe probe = new GridProbe().solid(0, 68, 0).solid(0, 69, 0);

        assertEquals(LobbySpawnSearch.Failure.BODY_OBSTRUCTED,
                LobbySpawnSearch.validate(CANONICAL, probe).failure());
    }

    @Test
    void insufficientHeadroomIsInvalid() {
        GridProbe probe = new GridProbe().solid(0, 68, 0).solid(0, 70, 0);

        assertEquals(LobbySpawnSearch.Failure.BODY_OBSTRUCTED,
                LobbySpawnSearch.validate(CANONICAL, probe).failure());
    }

    @Test
    void boundedFallbackSelectsNearestSafePosition() {
        GridProbe probe = new GridProbe()
                .solid(3, 68, 0)
                .solid(1, 68, 0);

        Optional<LobbySpawnSearch.Position> result = LobbySpawnSearch.findNearest(
                CANONICAL, BOUNDS, 4, 2, probe);

        assertEquals(Optional.of(new LobbySpawnSearch.Position(1, 69, 0)), result);
    }

    @Test
    void fallbackNeverLeavesConfiguredRadius() {
        GridProbe probe = new GridProbe().solid(3, 68, 0);

        assertTrue(LobbySpawnSearch.findNearest(CANONICAL, BOUNDS, 2, 2, probe).isEmpty());
    }

    @Test
    void missingSafePositionReturnsControlledFailure() {
        assertTrue(LobbySpawnSearch.findNearest(
                CANONICAL, BOUNDS, 4, 2, new GridProbe()).isEmpty());
    }

    private static final class GridProbe implements LobbySpawnSearch.CollisionProbe {
        private final Set<LobbySpawnSearch.Position> solid = new HashSet<>();

        GridProbe solid(int x, int y, int z) {
            solid.add(new LobbySpawnSearch.Position(x, y, z));
            return this;
        }

        @Override
        public boolean chunkAvailable(LobbySpawnSearch.Position position) {
            return true;
        }

        @Override
        public boolean hasSupportingCollision(LobbySpawnSearch.Position position) {
            return solid.contains(new LobbySpawnSearch.Position(
                    position.x(), position.y() - 1, position.z()));
        }

        @Override
        public boolean bodyIntersectsCollision(LobbySpawnSearch.Position position) {
            return solid.contains(position)
                    || solid.contains(new LobbySpawnSearch.Position(
                    position.x(), position.y() + 1, position.z()));
        }
    }
}
