package com.makar.tacticaltablet.airdrop;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3f;

public final class AirdropVisualHelper {

    private static final DustParticleOptions RED_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.0F, 0.0F), 1.8F);
    private static final DustParticleOptions GREEN_DUST = new DustParticleOptions(new Vector3f(0.0F, 1.0F, 0.0F), 1.8F);

    private AirdropVisualHelper() {
    }

    public static void spawnMarkerParticles(ServerLevel level, BlockPos chestPos, boolean green) {
        if (level == null || chestPos == null) return;

        double x = chestPos.getX() + 0.5D;
        double y = chestPos.getY() + 1.25D;
        double z = chestPos.getZ() + 0.5D;

        level.sendParticles(green ? GREEN_DUST : RED_DUST, x, y + 1.0D, z, 18, 0.35D, 1.2D, 0.35D, 0.02D);
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y, z, 2, 0.12D, 0.25D, 0.12D, 0.01D);
    }

    public static void spawnFallingCrateParticles(ServerLevel level, double x, double y, double z) {
        if (level == null) return;
        level.sendParticles(ParticleTypes.SMOKE, x, y, z, 4, 0.25D, 0.15D, 0.25D, 0.01D);
    }
}
