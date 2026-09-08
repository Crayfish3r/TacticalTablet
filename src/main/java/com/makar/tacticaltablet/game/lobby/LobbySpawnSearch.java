package com.makar.tacticaltablet.game.lobby;

import java.util.Optional;

final class LobbySpawnSearch {
    private LobbySpawnSearch() {
    }

    static Validation validate(Position position, CollisionProbe probe) {
        if (!probe.chunkAvailable(position)) return Validation.invalid(Failure.CHUNK_UNAVAILABLE);
        if (!probe.hasSupportingCollision(position)) return Validation.invalid(Failure.NO_SUPPORT);
        if (probe.bodyIntersectsCollision(position)) return Validation.invalid(Failure.BODY_OBSTRUCTED);
        return Validation.safe();
    }

    static Optional<Position> findNearest(
            Position canonical,
            Bounds bounds,
            int horizontalRadius,
            int verticalRadius,
            CollisionProbe probe
    ) {
        if (horizontalRadius < 0 || verticalRadius < 0) return Optional.empty();

        Position best = null;
        long bestDistance = Long.MAX_VALUE;
        int minX = Math.max(bounds.minX(), canonical.x() - horizontalRadius);
        int maxX = Math.min(bounds.maxX(), canonical.x() + horizontalRadius);
        int minY = Math.max(bounds.minY(), canonical.y() - verticalRadius);
        int maxY = Math.min(bounds.maxY(), canonical.y() + verticalRadius);
        int minZ = Math.max(bounds.minZ(), canonical.z() - horizontalRadius);
        int maxZ = Math.min(bounds.maxZ(), canonical.z() + horizontalRadius);
        long maximumHorizontalDistance = (long) horizontalRadius * horizontalRadius;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                long dx = x - canonical.x();
                long dz = z - canonical.z();
                long horizontalDistance = dx * dx + dz * dz;
                if (horizontalDistance > maximumHorizontalDistance) continue;

                for (int y = minY; y <= maxY; y++) {
                    Position candidate = new Position(x, y, z);
                    long dy = y - canonical.y();
                    long distance = horizontalDistance + dy * dy;
                    if (distance >= bestDistance || !validate(candidate, probe).valid()) continue;
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    interface CollisionProbe {
        boolean chunkAvailable(Position position);

        boolean hasSupportingCollision(Position position);

        boolean bodyIntersectsCollision(Position position);
    }

    record Position(int x, int y, int z) {
    }

    record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    }

    record Validation(boolean valid, Failure failure) {
        static Validation safe() {
            return new Validation(true, null);
        }

        static Validation invalid(Failure failure) {
            return new Validation(false, failure);
        }
    }

    enum Failure {
        CHUNK_UNAVAILABLE,
        NO_SUPPORT,
        BODY_OBSTRUCTED
    }
}
