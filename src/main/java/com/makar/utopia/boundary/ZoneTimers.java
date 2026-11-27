package com.makar.utopia.boundary;

/** One connected player's timers, using elapsed milliseconds on the server clock. */
final class ZoneTimers {
    private static final long CYCLE_MILLIS = 10 * 60 * 1000;
    static final int NEUTRAL_MILLIS = 30 * 1000;

    private CityZone zone = CityZone.OUTSIDE;
    private long updatedAt;
    private int portRemaining;
    private int neutralRemaining;
    private boolean neutralNoticePending;

    ZoneTimers(long now, int portRemaining) {
        updatedAt = now;
        this.portRemaining = portRemaining;
    }

    static long cycle(long now) { return now / CYCLE_MILLIS; }
    long cycle() { return cycle(updatedAt); }
    CityZone zone() { return zone; }
    int portRemaining() { return portRemaining; }
    int neutralRemaining() { return neutralRemaining; }
    boolean neutralNoticePending() { return neutralNoticePending; }

    void resumeNeutral(int remaining, boolean noticePending) {
        zone = CityZone.NEUTRAL;
        neutralRemaining = Math.max(0, Math.min(remaining, NEUTRAL_MILLIS));
        neutralNoticePending = neutralRemaining == 0 && noticePending;
    }

    void observe(long now, CityZone current) {
        long portStart = updatedAt;
        if (cycle() != cycle(now)) {
            portRemaining = CityZone.PORT_PROTECTION_MILLIS;
            portStart = cycle(now) * CYCLE_MILLIS;
        }
        if (zone == CityZone.PORT) portRemaining = remaining(portRemaining, now - portStart);
        if (zone == CityZone.NEUTRAL && neutralRemaining > 0) {
            neutralRemaining = remaining(neutralRemaining, now - updatedAt);
            neutralNoticePending = neutralRemaining == 0;
        }
        if (current != zone) {
            neutralRemaining = current == CityZone.NEUTRAL ? NEUTRAL_MILLIS : 0;
            neutralNoticePending = false;
            zone = current;
        }
        updatedAt = now;
    }

    boolean consumeNeutralNotice() {
        boolean pending = neutralNoticePending;
        neutralNoticePending = false;
        return pending;
    }

    private static int remaining(int allowance, long elapsed) {
        return (int) Math.max(0, allowance - elapsed);
    }
}
