package com.makar.tacticaltablet.tablet.client;

/** Pure world-to-radar geometry with zero-radius and boundary guards. */
final class ContractRadarMapper {
    private ContractRadarMapper() {
    }

    static int coordinate(int origin, int size, int world, int center, int radius) {
        int safeRadius = Math.max(1, radius);
        int minimum = center - safeRadius;
        int span = safeRadius * 2;
        float normalized = (world - minimum) / (float) span;
        int drawableSpan = Math.max(0, size - 1);
        return origin + Math.round(Math.max(0.0F, Math.min(1.0F, normalized)) * drawableSpan);
    }

    static int targetRadius(int mapSize, int areaRadius, int zoneRadius) {
        int safeZoneRadius = Math.max(1, zoneRadius);
        return Math.max(5, Math.min(18,
                Math.round(Math.max(0, areaRadius) / (safeZoneRadius * 2.0F) * mapSize)));
    }

    static double directionX(float yaw) {
        return -Math.sin(Math.toRadians(yaw));
    }

    static double directionY(float yaw) {
        return Math.cos(Math.toRadians(yaw));
    }
}
