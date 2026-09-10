package com.makar.tacticaltablet.casino;

/** Shared deterministic timeline, sampled from world time by both GUI and renderer. */
public final class CasinoAnimation {
    public static final int DURATION = 80;
    private CasinoAnimation() { }

    public static double reelDegrees(int reel, int previous, int target, double elapsed, int duration, long seed) {
        double tick = elapsed * DURATION / Math.max(1, duration);
        double start = 4 + reel * 2;
        double end = 60 + reel * 9;
        double t = Math.max(0, Math.min(1, (tick - start) / (end - start)));
        // Negative rotations bring symbol N (authored at +N*45 degrees) to the front.
        double travel = (5 + Math.floorMod(seed + reel, 3)) * 360.0
                + Math.floorMod(target - previous, 8) * 45.0;
        double eased = 1 - Math.pow(1 - t, 3);
        return -previous * 45.0 - travel * eased;
    }

    public static float leverDegrees(double elapsed, int duration) {
        double tick = elapsed * DURATION / Math.max(1, duration);
        if (tick < 0 || tick >= 14) return 0;
        double t = tick <= 6 ? tick / 6 : (14 - tick) / 8;
        return (float) (-55 * t * t * (3 - 2 * t));
    }
}
