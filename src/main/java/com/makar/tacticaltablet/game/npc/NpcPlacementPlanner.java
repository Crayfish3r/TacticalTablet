package com.makar.tacticaltablet.game.npc;

import com.makar.tacticaltablet.game.zone.ZoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Bounded site preflight; NpcSpawnService owns final per-member placement and atomic admission. */
public final class NpcPlacementPlanner {
    // API 0.2.0 may place a group on a +/-12 grid. Reserve the entire envelope,
    // including rounding/body width, for border, player and inter-group distances.
    static final double ENVELOPE_RADIUS = 19;

    public Optional<Vec3> plan(ServerLevel level, List<ServerPlayer> players, List<Vec3> groups,
                               int size, NpcEncounterConfig c, RandomGenerator random) {
        var rtp = ZoneManager.getActiveRtpSettings();
        if (players.isEmpty() || !rtp.valid()) return Optional.empty();
        for (int attempt = 0; attempt < c.maxSpawnAttempts; attempt++) {
            var anchor = players.get(random.nextInt(players.size()));
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = c.minDistanceFromPlayers + ENVELOPE_RADIUS
                    + random.nextDouble() * Math.max(0, c.maxDistanceFromPlayers - c.minDistanceFromPlayers - 2 * ENVELOPE_RADIUS);
            int x = (int) Math.floor(anchor.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(anchor.getZ() + Math.sin(angle) * distance);
            if (!loaded(level, x - 14, z - 14, x + 14, z + 14)) continue;
            var border = level.getWorldBorder();
            if (!NpcPlacementBounds.inside(x + .5, z + .5, border.getMinX(), border.getMaxX(), border.getMinZ(), border.getMaxZ(), ENVELOPE_RADIUS)) continue;
            var arenaBorder = level.getServer().overworld().getWorldBorder();
            if (!NpcPlacementBounds.inside(x + .5, z + .5, arenaBorder.getMinX(), arenaBorder.getMaxX(),
                    arenaBorder.getMinZ(), arenaBorder.getMaxZ(), ENVELOPE_RADIUS)) continue;
            if (rtp.mode() == ZoneManager.RtpPlacementMode.FIXED_Y_BOX
                    && !NpcPlacementBounds.inside(x + .5, z + .5, rtp.minX(), rtp.maxX(), rtp.minZ(), rtp.maxZ(), ENVELOPE_RADIUS)) continue;
            var origin = new Vec3(x + .5, rtp.mode() == ZoneManager.RtpPlacementMode.FIXED_Y_BOX ? rtp.spawnY()
                    : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + .5);
            if (level.players().stream().anyMatch(p -> horizontal(p.position(), origin) < square(c.minDistanceFromPlayers + ENVELOPE_RADIUS))) continue;
            if (players.stream().noneMatch(p -> horizontal(p.position(), origin) <= square(c.maxDistanceFromPlayers - ENVELOPE_RADIUS))) continue;
            if (groups.stream().anyMatch(p -> horizontal(p, origin) < square(c.minDistanceBetweenGroups + 2 * ENVELOPE_RADIUS))) continue;
            int safeSlots = 0;
            int spacing = size == 1 ? 2 : 4;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    var feet = BlockPos.containing(origin).offset(dx * spacing, 0, dz * spacing);
                    if (safe(level, feet, rtp.requiredSolidBlocksBelow())) safeSlots++;
                }
            }
            // API can adjust vertical placement by +/-1. For FIXED_Y_BOX accept only a
            // completely safe, flat candidate grid so every API choice remains at spawnY.
            if (safeSlots >= size && (rtp.mode() != ZoneManager.RtpPlacementMode.FIXED_Y_BOX || safeSlots == 48))
                return Optional.of(origin);
        }
        return Optional.empty();
    }

    static double horizontal(Vec3 a, Vec3 b) { return square(a.x - b.x) + square(a.z - b.z); }
    private static double square(double v) { return v * v; }

    private static boolean loaded(ServerLevel level, int minX, int minZ, int maxX, int maxZ) {
        for (int x = minX >> 4; x <= maxX >> 4; x++)
            for (int z = minZ >> 4; z <= maxZ >> 4; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }

    private static boolean safe(ServerLevel level, BlockPos feet, int solidBelow) {
        if (feet.getY() - solidBelow - 1 < level.getMinBuildHeight() || feet.getY() + 2 >= level.getMaxBuildHeight()) return false;
        for (int i = -2; i <= solidBelow + 1; i++) {
            var pos = feet.below(i);
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || hazard(state) || level.getBlockEntity(pos) != null) return false;
            if (i > 0 && !state.isFaceSturdy(level, pos, Direction.UP)) return false;
        }
        var body = new AABB(feet.getX() + .1, feet.getY() + .01, feet.getZ() + .1,
                feet.getX() + .9, feet.getY() + 2, feet.getZ() + .9);
        return level.noCollision(null, body) && level.getEntitiesOfClass(LivingEntity.class, body, LivingEntity::isAlive).isEmpty();
    }

    private static boolean hazard(BlockState s) {
        return s.getBlock() instanceof BaseFireBlock || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS)
                || s.is(Blocks.CAMPFIRE) || s.is(Blocks.SOUL_CAMPFIRE) || s.is(Blocks.SWEET_BERRY_BUSH)
                || s.is(Blocks.WITHER_ROSE) || s.is(Blocks.POWDER_SNOW);
    }
}
