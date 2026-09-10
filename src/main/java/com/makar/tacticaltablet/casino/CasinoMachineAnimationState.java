package com.makar.tacticaltablet.casino;

/** Server-owned reservation and visual timeline. No economy or Minecraft runtime objects. */
public final class CasinoMachineAnimationState {
    private boolean reserved;
    private boolean spinning;
    private long start;
    private int duration;
    private long seed;
    private CasinoReelResult previous = CasinoReelResult.IDLE;
    private CasinoReelResult target = CasinoReelResult.IDLE;

    public boolean reserve(long now) {
        finish(now);
        if (reserved || spinning) return false;
        reserved = true;
        return true;
    }

    public void release() { reserved = false; }

    public void start(long now, long animationSeed, CasinoReelResult result) {
        if (!reserved) throw new IllegalStateException("Machine spin was not reserved");
        previous = target;
        target = result;
        start = now;
        duration = CasinoAnimation.DURATION;
        seed = animationSeed;
        reserved = false;
        spinning = true;
    }

    public boolean finish(long now) {
        // A template copied from another world may contain a future start tick.
        if (!spinning || (now >= start && now - start < duration)) return false;
        spinning = false;
        previous = target;
        return true;
    }

    public void restore(boolean active, long startTick, int ticks, long animationSeed,
                        CasinoReelResult from, CasinoReelResult to) {
        reserved = false;
        spinning = active && ticks > 0;
        start = startTick;
        duration = Math.max(0, Math.min(CasinoAnimation.DURATION, ticks));
        seed = animationSeed;
        previous = from;
        target = to;
    }

    public boolean busy() { return reserved || spinning; }
    public boolean spinning() { return spinning; }
    public long startTick() { return start; }
    public int duration() { return duration; }
    public long seed() { return seed; }
    public CasinoReelResult previous() { return previous; }
    public CasinoReelResult target() { return target; }
}
