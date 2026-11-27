package com.makar.tacticaltablet.game.arena;

import net.minecraft.server.level.ServerLevel;

/** A separate static policy: no phase index, final reveal or respawn cutoff. */
public final class ArenaZonePolicy {
    public static final double SIZE = 360;
    private final java.util.UUID matchId;
    public ArenaZonePolicy(java.util.UUID matchId) { this.matchId = java.util.Objects.requireNonNull(matchId); }
    public java.util.UUID matchId() { return matchId; }
    public void apply(ServerLevel level, double centerX, double centerZ) {
        if (level == null || !Double.isFinite(centerX) || !Double.isFinite(centerZ))
            throw new IllegalStateException("Arena map center/dimension is unavailable");
        var border = level.getWorldBorder();
        border.setCenter(centerX, centerZ); border.setSize(SIZE);
        border.setDamageSafeZone(0); border.setDamagePerBlock(2);
        border.setWarningBlocks(8); border.setWarningTime(15);
    }
}
