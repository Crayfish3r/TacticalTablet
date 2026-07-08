package com.makar.tacticaltablet.game.extraction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ExtractionPointVisualHelper {
    public enum VisualMode {
        NORMAL,
        CAPTURING,
        CONTESTED,
        CAPTURED,
        ENDING
    }

    private static final Map<String, DustParticleOptions> PARTICLES = new ConcurrentHashMap<>();

    private ExtractionPointVisualHelper() {
    }

    public static void spawnRing(ServerLevel level, ExtractionPointData data, ExtractionPointConfig config, VisualMode mode) {
        if (level == null || data == null || data.center == null || config == null || !config.particleEnabled) return;

        DustParticleOptions particle = particle(mode, config.particleScale);
        int basePoints = config.ringPoints * 2;
        int points = mode == VisualMode.CAPTURED ? basePoints * 2 : basePoints;
        double radius = data.radius;
        double visibleDistanceSq = config.particleVisibleDistance * config.particleVisibleDistance;
        double bottomY = data.center.getY() - data.halfHeight;
        double topY = data.center.getY() + data.halfHeight;

        for (double y = bottomY; y <= topY + 0.1D; y += 5.0D) {
            for (int index = 0; index < points; index++) {
                double angle = (Math.PI * 2.0D * index) / points;
                double x = data.center.getX() + 0.5D + Math.cos(angle) * radius;
                double z = data.center.getZ() + 0.5D + Math.sin(angle) * radius;
                double particleY = y + 0.15D;

                for (ServerPlayer player : level.players()) {
                    if (player.distanceToSqr(x, particleY, z) > visibleDistanceSq) continue;
                    level.sendParticles(player, particle, true, x, particleY, z, 1, 0.02D, 0.01D, 0.02D, 0.0D);
                }
            }
        }
    }

    public static void playCaptured(ServerLevel level, BlockPos center) {
        if (level == null || center == null) return;
        level.playSound(null, center, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.playSound(null, center, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    private static Vector3f color(VisualMode mode) {
        return switch (mode) {
            case CAPTURING -> new Vector3f(0.1F, 0.9F, 0.25F);
            case CONTESTED -> new Vector3f(1.0F, 0.25F, 0.05F);
            case CAPTURED -> new Vector3f(0.1F, 1.0F, 0.25F);
            case ENDING -> new Vector3f(0.35F, 0.35F, 0.35F);
            case NORMAL -> new Vector3f(1.0F, 0.85F, 0.35F);
        };
    }

    private static DustParticleOptions particle(VisualMode mode, float scale) {
        String key = mode.name() + ":" + scale;
        return PARTICLES.computeIfAbsent(key, ignored -> new DustParticleOptions(color(mode), scale));
    }
}
