package com.makar.tacticaltablet.game.npc;

/** Geometry of an entire API placement envelope, independent of world/chunk access. */
final class NpcPlacementBounds {
    static boolean inside(double x, double z, double minX, double maxX, double minZ, double maxZ, double margin) {
        return Double.isFinite(x) && Double.isFinite(z) && Double.isFinite(margin) && margin >= 0
                && x > minX + margin && x < maxX - margin && z > minZ + margin && z < maxZ - margin;
    }
}
