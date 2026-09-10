package com.makar.tacticaltablet.casino;

/** Facts are obtained from the server, never from C2S payloads. */
public final class CasinoMachineAccess {
    public static final double MAX_USE_DISTANCE_SQR = 64.0;
    private CasinoMachineAccess() { }
    public static boolean matchesAnchor(java.util.UUID sessionId, String dimension, net.minecraft.core.BlockPos machinePos,
                                        java.util.UUID id, String world, net.minecraft.core.BlockPos pos) {
        return sessionId.equals(id) && java.util.Objects.equals(dimension, world)
                && java.util.Objects.equals(machinePos, pos);
    }
    public static boolean allows(boolean online, boolean sameDimension, boolean casinoBlock,
                                 boolean sameBlockEntity, double distanceSquared, boolean matchingMenu) {
        return online && sameDimension && casinoBlock && sameBlockEntity && matchingMenu
                && Double.isFinite(distanceSquared) && distanceSquared <= MAX_USE_DISTANCE_SQR;
    }
}
