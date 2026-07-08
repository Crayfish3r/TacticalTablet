package com.makar.tacticaltablet.game.teleport;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.GameStateManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SafeTeleport {

    private static final int POOL_POINTS_PER_PLAYER = 8;
    private static final int MIN_POOL_SIZE = 32;
    private static final int MAX_POOL_SIZE = 256;
    private static final int POOL_ATTEMPT_MULTIPLIER = 80;
    private static final int POOL_ATTEMPTS_PER_TICK = 48;
    private static final int FALLBACK_ATTEMPTS_PER_CALL = 96;
    private static final int POOL_CHUNK_LOADS_PER_TICK = 4;
    private static final int FALLBACK_CHUNK_LOADS_PER_CALL = 8;
    private static final int TEAM_CLUSTER_CHUNK_LOADS_PER_CALL = 4;
    private static final int TEST_CHUNK_LOADS_PER_CALL = 16;
    private static final int[][] TEAM_OFFSETS = new int[][]{
            {0, 0},
            {3, 0},
            {-3, 0},
            {0, 3},
            {0, -3},
            {4, 3},
            {-4, -3}
    };
    private static final int[] LOCAL_SEARCH_RADII = new int[]{0, 3, 6, 10, 14};
    private static final double MIN_BORDER_MARGIN = 6.0;
    private static final double PREFERRED_BORDER_MARGIN = 24.0;
    private static final double MIN_PLAYER_DISTANCE = 28.0;
    private static final double MAX_PLAYER_DISTANCE = 96.0;
    private static final double PLAYER_DISTANCE_BORDER_FACTOR = 0.45D;
    private static final int RECENT_SPAWN_MEMORY = 64;
    private static final double TWO_PI = Math.PI * 2.0;

    private static final List<BlockPos> preparedSpawns = new ArrayList<>();
    private static final Set<Long> preparedSpawnKeys = new HashSet<>();
    private static final ArrayDeque<BlockPos> recentSpawns = new ArrayDeque<>();
    private static RandomSource poolRandom = RandomSource.create();
    private static boolean poolPreparing = false;
    private static int poolTarget = 0;
    private static int poolAttempts = 0;
    private static int poolMaxAttempts = 0;
    private static int chunkLoadBudget = 0;

    public static synchronized PoolStatus preparePool(MinecraftServer server) {
        return beginPoolPreparation(server);
    }

    public static synchronized PoolStatus beginPoolPreparation(MinecraftServer server) {
        preparedSpawns.clear();
        preparedSpawnKeys.clear();
        recentSpawns.clear();
        poolPreparing = false;
        poolTarget = 0;
        poolAttempts = 0;
        poolMaxAttempts = 0;
        poolRandom = RandomSource.create();

        ServerLevel overworld = GameStateManager.getOverworld(server);
        if (overworld == null) {
            return new PoolStatus(0, 0, 0, 0.0D, 0.0D);
        }

        int players = Math.max(1, server.getPlayerList().getPlayerCount());
        poolTarget = Math.min(MAX_POOL_SIZE, Math.max(MIN_POOL_SIZE, players * POOL_POINTS_PER_PLAYER));
        poolMaxAttempts = poolTarget * POOL_ATTEMPT_MULTIPLIER;
        poolPreparing = true;

        WorldBorder border = overworld.getWorldBorder();

        TacticalTabletMod.LOGGER.info(
                "Started RTP spawn pool preparation. target={}, maxAttempts={}, borderSize={}, margin={}",
                poolTarget,
                poolMaxAttempts,
                border.getSize(),
                getSpawnBorderMargin(border)
        );

        return getPoolStatus(overworld);
    }

    public static synchronized PoolStatus tickPool(MinecraftServer server) {
        ServerLevel overworld = GameStateManager.getOverworld(server);
        if (overworld == null) {
            poolPreparing = false;
            return new PoolStatus(poolTarget, preparedSpawns.size(), poolAttempts, 0.0D, 0.0D);
        }

        if (!poolPreparing) {
            return getPoolStatus(overworld);
        }

        fillPoolBatch(overworld, POOL_ATTEMPTS_PER_TICK);

        if (preparedSpawns.size() >= poolTarget || poolAttempts >= poolMaxAttempts) {
            poolPreparing = false;
            WorldBorder border = overworld.getWorldBorder();
            TacticalTabletMod.LOGGER.info(
                    "Finished RTP spawn pool preparation. prepared={} target={} attempts={} borderSize={}, margin={}",
                    preparedSpawns.size(),
                    poolTarget,
                    poolAttempts,
                    border.getSize(),
                    getSpawnBorderMargin(border)
            );
        }

        return getPoolStatus(overworld);
    }

    public static synchronized boolean isPoolPreparing() {
        return poolPreparing;
    }

    public static synchronized TestResult testPoints(MinecraftServer server, int requested) {
        ServerLevel overworld = GameStateManager.getOverworld(server);
        if (overworld == null) {
            return new TestResult(Math.max(0, requested), 0, 0, 0.0D, 0.0D, List.of());
        }

        int target = Math.min(MAX_POOL_SIZE, Math.max(1, requested));
        int maxAttempts = target * POOL_ATTEMPT_MULTIPLIER;
        RandomSource random = RandomSource.create();
        List<BlockPos> valid = new ArrayList<>();
        List<PlayerPosition> validPositions = new ArrayList<>();
        Set<Long> keys = new HashSet<>();
        int attempts = 0;

        chunkLoadBudget = TEST_CHUNK_LOADS_PER_CALL;
        try {
            while (valid.size() < target && attempts < maxAttempts) {
                attempts++;
                boolean strictDistance = attempts < (maxAttempts * 3 / 4);
                BlockPos position = createRandomSafePoint(overworld, random, validPositions, strictDistance);
                if (position == null || !keys.add(position.asLong())) continue;

                valid.add(position);
                validPositions.add(toPlayerPosition(position));
            }
        } finally {
            chunkLoadBudget = 0;
        }

        WorldBorder border = overworld.getWorldBorder();
        return new TestResult(
                target,
                valid.size(),
                attempts,
                border.getSize(),
                getSpawnBorderMargin(border),
                List.copyOf(valid.subList(0, Math.min(8, valid.size())))
        );
    }

    public static synchronized int getPreparedCount() {
        return preparedSpawns.size();
    }

    public static synchronized PoolStatus getPoolStatus(MinecraftServer server) {
        return getPoolStatus(GameStateManager.getOverworld(server));
    }

    public static synchronized void clearPool() {
        preparedSpawns.clear();
        preparedSpawnKeys.clear();
        recentSpawns.clear();
        poolPreparing = false;
        poolTarget = 0;
        poolAttempts = 0;
        poolMaxAttempts = 0;
    }

    public static boolean teleport(ServerPlayer player) {
        if (player == null) return false;

        ServerLevel overworld = GameStateManager.getOverworld(player.server);
        if (overworld == null) return false;

        RandomSource random = RandomSource.create();
        BlockPos safePos = takeBestPreparedPoint(player, overworld, random);

        if (safePos == null) {
            safePos = findBestFallbackPoint(player, overworld, random);
        }

        if (safePos == null) {
            WorldBorder border = overworld.getWorldBorder();
            TacticalTabletMod.LOGGER.warn(
                    "Safe RTP point not found for {}. borderCenter=({}, {}), borderSize={}, attempts={}, preparedPool={}",
                    player.getGameProfile().getName(),
                    border.getCenterX(),
                    border.getCenterZ(),
                    border.getSize(),
                    FALLBACK_ATTEMPTS_PER_CALL,
                    getPreparedCount()
            );
            return false;
        }

        player.changeDimension(overworld);
        player.teleportTo(
                overworld,
                safePos.getX() + 0.5,
                safePos.getY(),
                safePos.getZ() + 0.5,
                random.nextFloat() * 360.0F,
                0.0F
        );
        rememberSpawn(safePos);

        return true;
    }

    public static boolean teleportTeam(List<ServerPlayer> players) {
        if (players == null || players.isEmpty()) return false;
        if (players.size() == 1) return teleport(players.get(0));

        ServerPlayer anchorPlayer = players.get(0);
        if (anchorPlayer == null) return false;

        ServerLevel overworld = GameStateManager.getOverworld(anchorPlayer.server);
        if (overworld == null) return false;

        RandomSource random = RandomSource.create();
        BlockPos anchor = takeBestPreparedPoint(anchorPlayer, overworld, random);
        if (anchor == null) {
            anchor = findBestFallbackPoint(anchorPlayer, overworld, random);
        }
        if (anchor == null) return false;

        List<BlockPos> positions;
        chunkLoadBudget = TEAM_CLUSTER_CHUNK_LOADS_PER_CALL;
        try {
            positions = findTeamCluster(overworld, anchor, players.size());
        } finally {
            chunkLoadBudget = 0;
        }
        if (positions.size() < players.size()) {
            TacticalTabletMod.LOGGER.warn(
                    "Safe team RTP cluster not found. requested={}, found={}, anchor={}, preparedPool={}",
                    players.size(),
                    positions.size(),
                    anchor,
                    getPreparedCount()
            );
            return false;
        }

        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            if (player == null) continue;

            BlockPos safePos = positions.get(i);
            player.changeDimension(overworld);
            player.teleportTo(
                    overworld,
                    safePos.getX() + 0.5,
                    safePos.getY(),
                    safePos.getZ() + 0.5,
                    random.nextFloat() * 360.0F,
                    0.0F
            );
            rememberSpawn(safePos);
        }

        return true;
    }

    private static List<BlockPos> findTeamCluster(ServerLevel overworld, BlockPos anchor, int count) {
        List<BlockPos> positions = new ArrayList<>();

        for (int[] offset : TEAM_OFFSETS) {
            if (positions.size() >= count) break;

            BlockPos base = anchor.offset(offset[0], 0, offset[1]);
            BlockPos safe = findSafeNear(overworld, base.getX(), base.getZ(), getSpawnBorderMargin(overworld.getWorldBorder()));
            if (safe == null) continue;
            if (!positions.contains(safe)) {
                positions.add(safe);
            }
        }

        return positions;
    }

    private static int fillPoolBatch(ServerLevel overworld, int maxBatchAttempts) {
        List<PlayerPosition> preparedPositions = blockPositionsToPlayerPositions(preparedSpawns);
        int batchAttempts = 0;

        chunkLoadBudget = POOL_CHUNK_LOADS_PER_TICK;
        try {
            while (preparedSpawns.size() < poolTarget
                    && poolAttempts < poolMaxAttempts
                    && batchAttempts < maxBatchAttempts) {
                batchAttempts++;
                poolAttempts++;
                boolean strictDistance = poolAttempts < (poolMaxAttempts * 3 / 4);
                BlockPos position = createRandomSafePoint(
                        overworld,
                        poolRandom,
                        preparedPositions,
                        strictDistance
                );
                if (position == null || !preparedSpawnKeys.add(position.asLong())) continue;

                preparedSpawns.add(position);
                preparedPositions.add(toPlayerPosition(position));
            }
        } finally {
            chunkLoadBudget = 0;
        }

        return batchAttempts;
    }

    private static PoolStatus getPoolStatus(ServerLevel overworld) {
        if (overworld == null) {
            return new PoolStatus(poolTarget, preparedSpawns.size(), poolAttempts, 0.0D, 0.0D);
        }

        WorldBorder border = overworld.getWorldBorder();
        return new PoolStatus(
                poolTarget,
                preparedSpawns.size(),
                poolAttempts,
                border.getSize(),
                getSpawnBorderMargin(border)
        );
    }

    private static synchronized BlockPos takeBestPreparedPoint(
            ServerPlayer player,
            ServerLevel overworld,
            RandomSource random
    ) {
        pruneInvalidPreparedSpawns(overworld);
        if (preparedSpawns.isEmpty()) return null;

        List<PlayerPosition> occupiedPositions = snapshotOccupiedPositions(player, overworld);
        List<PlayerPosition> recentPositions = snapshotRecentSpawnPositions();
        double minPlayerDistance = getMinPlayerDistance(overworld.getWorldBorder());

        if (occupiedPositions.isEmpty() && recentPositions.isEmpty()) {
            int index = random.nextInt(preparedSpawns.size());
            BlockPos position = preparedSpawns.remove(index);
            preparedSpawnKeys.remove(position.asLong());
            return isSafeSpawn(overworld, position) ? position : null;
        }

        BlockPos bestStrictPosition = null;
        double bestStrictScore = -1.0D;
        BlockPos bestRelaxedPosition = null;
        double bestRelaxedScore = -1.0D;

        for (BlockPos position : preparedSpawns) {
            double score = scoreSpawnPosition(occupiedPositions, recentPositions, position);
            if (score > bestRelaxedScore) {
                bestRelaxedScore = score;
                bestRelaxedPosition = position;
            }

            if ((occupiedPositions.isEmpty() || isFarEnough(occupiedPositions, position, minPlayerDistance))
                    && score > bestStrictScore) {
                bestStrictScore = score;
                bestStrictPosition = position;
            }
        }

        BlockPos selected = bestStrictPosition;
        if (selected == null && occupiedPositions.isEmpty()) {
            selected = bestRelaxedPosition;
        }
        if (selected == null) return null;

        BlockPos position = selected;
        preparedSpawns.remove(position);
        preparedSpawnKeys.remove(position.asLong());
        return position;
    }

    private static void pruneInvalidPreparedSpawns(ServerLevel overworld) {
        for (int i = preparedSpawns.size() - 1; i >= 0; i--) {
            BlockPos position = preparedSpawns.get(i);
            if (!isSafeSpawn(overworld, position)) {
                preparedSpawns.remove(i);
                preparedSpawnKeys.remove(position.asLong());
            }
        }
    }

    private static BlockPos findBestFallbackPoint(ServerPlayer player, ServerLevel overworld, RandomSource random) {
        List<PlayerPosition> occupiedPositions = snapshotOccupiedPositions(player, overworld);
        List<PlayerPosition> recentPositions = snapshotRecentSpawnPositions();
        double minPlayerDistance = getMinPlayerDistance(overworld.getWorldBorder());

        BlockPos bestStrictPosition = null;
        double bestStrictScore = -1.0D;
        BlockPos bestRelaxedPosition = null;
        double bestRelaxedScore = -1.0D;

        chunkLoadBudget = FALLBACK_CHUNK_LOADS_PER_CALL;
        try {
            for (int i = 0; i < FALLBACK_ATTEMPTS_PER_CALL; i++) {
                BlockPos position = createRandomSafePoint(overworld, random, List.of(), false);
                if (position == null) continue;

                double score = scoreSpawnPosition(occupiedPositions, recentPositions, position);
                if (score > bestRelaxedScore) {
                    bestRelaxedScore = score;
                    bestRelaxedPosition = position;
                }

                if ((occupiedPositions.isEmpty() || isFarEnough(occupiedPositions, position, minPlayerDistance))
                        && score > bestStrictScore) {
                    bestStrictScore = score;
                    bestStrictPosition = position;
                }
            }
        } finally {
            chunkLoadBudget = 0;
        }

        return bestStrictPosition != null ? bestStrictPosition : bestRelaxedPosition;
    }

    private static synchronized void rememberSpawn(BlockPos position) {
        recentSpawns.addLast(position.immutable());

        while (recentSpawns.size() > RECENT_SPAWN_MEMORY) {
            recentSpawns.removeFirst();
        }
    }

    private static synchronized List<PlayerPosition> snapshotRecentSpawnPositions() {
        List<PlayerPosition> positions = new ArrayList<>();

        for (BlockPos position : recentSpawns) {
            positions.add(toPlayerPosition(position));
        }

        return positions;
    }

    private static List<PlayerPosition> blockPositionsToPlayerPositions(List<BlockPos> positions) {
        List<PlayerPosition> result = new ArrayList<>();

        for (BlockPos position : positions) {
            result.add(toPlayerPosition(position));
        }

        return result;
    }

    private static PlayerPosition toPlayerPosition(BlockPos position) {
        return new PlayerPosition(position.getX() + 0.5D, position.getZ() + 0.5D);
    }

    private static double scoreSpawnPosition(
            List<PlayerPosition> occupiedPositions,
            List<PlayerPosition> recentPositions,
            BlockPos position
    ) {
        double x = position.getX() + 0.5D;
        double z = position.getZ() + 0.5D;
        double score = 0.0D;

        if (!occupiedPositions.isEmpty()) {
            score += nearestDistanceSq(occupiedPositions, x, z) * 4.0D;
        }

        if (!recentPositions.isEmpty()) {
            score += nearestDistanceSq(recentPositions, x, z);
        }

        return score;
    }

    private static BlockPos createRandomSafePoint(
            ServerLevel overworld,
            RandomSource random,
            List<PlayerPosition> occupiedPositions,
            boolean strictDistance
    ) {
        WorldBorder border = overworld.getWorldBorder();
        double centerX = border.getCenterX();
        double centerZ = border.getCenterZ();
        double borderRadius = border.getSize() / 2.0D;
        double margin = getSpawnBorderMargin(border);

        if (borderRadius <= margin + 2.0D) {
            return null;
        }

        double minRadius = 0.0D;
        double maxRadius = Math.max(1.0D, borderRadius - margin);
        double minDistance = getMinPlayerDistance(border);

        double angle = random.nextDouble() * TWO_PI;
        double radius = Math.sqrt(random.nextDouble()) * (maxRadius - minRadius) + minRadius;

        double x = centerX + Math.cos(angle) * radius;
        double z = centerZ + Math.sin(angle) * radius;

        BlockPos position = findSafeNear(overworld, x, z, margin);
        if (position == null) {
            return null;
        }

        if (strictDistance && !isFarEnough(occupiedPositions, position, minDistance)) {
            return null;
        }

        return position;
    }

    private static BlockPos findSafeNear(ServerLevel level, double x, double z, double margin) {
        int baseX = (int) Math.floor(x);
        int baseZ = (int) Math.floor(z);

        for (int radius : LOCAL_SEARCH_RADII) {
            if (radius == 0) {
                BlockPos position = getSafeColumnPosition(level, baseX, baseZ, margin);
                if (position != null) return position;
                continue;
            }

            for (int dx = -radius; dx <= radius; dx += 3) {
                for (int dz = -radius; dz <= radius; dz += 3) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;

                    BlockPos position = getSafeColumnPosition(level, baseX + dx, baseZ + dz, margin);
                    if (position != null) return position;
                }
            }
        }

        return null;
    }

    private static BlockPos getSafeColumnPosition(ServerLevel level, int x, int z, double margin) {
        BlockPos column = new BlockPos(x, 0, z);
        if (!isInsideBorder(level.getWorldBorder(), x + 0.5D, z + 0.5D, margin)) {
            return null;
        }

        if (!ensureChunkAvailable(level, x >> 4, z >> 4)) {
            return null;
        }

        BlockPos pos = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                column
        );

        if (!isInsideBorder(level.getWorldBorder(), pos.getX() + 0.5D, pos.getZ() + 0.5D, margin)) {
            return null;
        }

        return isSafeSpawn(level, pos) ? pos : null;
    }

    private static boolean ensureChunkAvailable(ServerLevel level, int chunkX, int chunkZ) {
        if (level.hasChunk(chunkX, chunkZ)) return true;
        if (chunkLoadBudget <= 0) return false;

        chunkLoadBudget--;
        level.getChunk(chunkX, chunkZ);
        return true;
    }

    private static double getSpawnBorderMargin(WorldBorder border) {
        double borderRadius = border.getSize() / 2.0D;
        if (borderRadius <= PREFERRED_BORDER_MARGIN + 16.0D) {
            return Math.max(MIN_BORDER_MARGIN, borderRadius * 0.15D);
        }

        return PREFERRED_BORDER_MARGIN;
    }

    private static double getMinPlayerDistance(WorldBorder border) {
        double playableRadius = Math.max(1.0D, (border.getSize() / 2.0D) - getSpawnBorderMargin(border));
        double scaledDistance = playableRadius * PLAYER_DISTANCE_BORDER_FACTOR;

        return Math.min(MAX_PLAYER_DISTANCE, Math.max(MIN_PLAYER_DISTANCE, scaledDistance));
    }

    private static boolean isInsideBorder(WorldBorder border, double x, double z, double margin) {
        return x > border.getMinX() + margin
                && x < border.getMaxX() - margin
                && z > border.getMinZ() + margin
                && z < border.getMaxZ() - margin;
    }

    private static List<PlayerPosition> snapshotOccupiedPositions(ServerPlayer player, ServerLevel overworld) {
        List<PlayerPosition> positions = new ArrayList<>();

        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other == player) continue;
            if (!other.level().dimension().equals(overworld.dimension())) continue;
            if (!other.getTags().contains("war.playing")) continue;

            positions.add(new PlayerPosition(other.getX(), other.getZ()));
        }

        return positions;
    }

    private static boolean isFarEnough(List<PlayerPosition> positions, double x, double z, double minDistance) {
        return nearestDistanceSq(positions, x, z) >= minDistance * minDistance;
    }

    private static boolean isFarEnough(List<PlayerPosition> positions, BlockPos position, double minDistance) {
        return isFarEnough(positions, position.getX() + 0.5D, position.getZ() + 0.5D, minDistance);
    }

    private static double nearestDistanceSq(List<PlayerPosition> positions, double x, double z) {
        if (positions.isEmpty()) return Double.MAX_VALUE;

        double nearest = Double.MAX_VALUE;

        for (PlayerPosition position : positions) {
            double dx = position.x - x;
            double dz = position.z - z;
            nearest = Math.min(nearest, dx * dx + dz * dz);
        }

        return nearest;
    }

    private static boolean isSafeSpawn(ServerLevel level, BlockPos pos) {
        if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
        if (pos.getY() <= level.getMinBuildHeight() + 2) return false;
        if (pos.getY() >= level.getMaxBuildHeight() - 2) return false;

        WorldBorder border = level.getWorldBorder();
        if (!isInsideBorder(border, pos.getX() + 0.5D, pos.getZ() + 0.5D, getSpawnBorderMargin(border))) {
            return false;
        }

        BlockPos groundPos = pos.below();
        BlockState ground = level.getBlockState(groundPos);
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());

        if (level.getBlockEntity(groundPos) != null) return false;
        if (level.getBlockEntity(pos) != null) return false;
        if (level.getBlockEntity(pos.above()) != null) return false;
        if (!isStandable(level, groundPos, ground)) return false;
        if (!hasStableGroundBelow(level, groundPos)) return false;
        if (!isPassable(level, pos, feet)) return false;
        if (!isPassable(level, pos.above(), head)) return false;

        return !isHazard(ground) && !isHazard(feet) && !isHazard(head);
    }

    private static boolean hasStableGroundBelow(ServerLevel level, BlockPos groundPos) {
        for (int i = 1; i <= 3; i++) {
            BlockPos belowPos = groundPos.below(i);
            BlockState below = level.getBlockState(belowPos);

            if (below.isAir()) return false;
            if (!below.getFluidState().isEmpty()) return false;
            if (below.getCollisionShape(level, belowPos).isEmpty()) return false;
        }

        return true;
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) return false;
        if (state.is(BlockTags.LEAVES)) return false;
        if (!state.getFluidState().isEmpty()) return false;

        return !state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isPassable(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.getFluidState().isEmpty()) return false;

        return state.isAir() || state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isHazard(BlockState state) {
        return state.is(Blocks.CACTUS)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.POWDER_SNOW);
    }

    public record PoolStatus(int target, int prepared, int attempts, double borderSize, double margin) {
    }

    public record TestResult(
            int requested,
            int valid,
            int attempts,
            double borderSize,
            double margin,
            List<BlockPos> samples
    ) {
    }

    private record PlayerPosition(double x, double z) {
    }
}
