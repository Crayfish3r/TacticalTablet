package com.makar.utopia.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A bounded, north-up texture with one texel per world block. Updated only on client ticks. */
final class TabletTerrain implements AutoCloseable {
    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_RADIUS = 16;
    private static final int ORIGIN_STEP_CHUNKS = 4;
    static final int SIZE = 2 * CHUNK_RADIUS * CHUNK_SIZE;
    private static final int BACKGROUND_ABGR = 0xff302c29;
    private static final int NEW_TILES_PER_TICK = 4;
    private static final int REFRESH_AFTER_TICKS = 60;
    private static final List<ChunkPos> SAMPLE_ORDER = sampleOrder();

    private record Tile(LevelChunk chunk, int[] colors, long sampledAt) { }

    private final Map<Long, Tile> tiles = new HashMap<>();
    private ClientLevel level;
    private DynamicTexture texture;
    private int originChunkX;
    private int originChunkZ;
    private long ticks;
    private long revision;

    int originX() { return originChunkX * CHUNK_SIZE; }
    int originZ() { return originChunkZ * CHUNK_SIZE; }
    int textureId() { return texture == null ? -1 : texture.getId(); }
    long revision() { return revision; }

    void tick(ClientLevel currentLevel, double centerX, double centerZ) {
        boolean levelChanged = level != currentLevel;
        if (levelChanged) {
            tiles.clear();
            level = currentLevel;
        }
        ticks++;
        int anchorX = anchorChunk(centerX);
        int anchorZ = anchorChunk(centerZ);
        int nextX = anchorX - CHUNK_RADIUS;
        int nextZ = anchorZ - CHUNK_RADIUS;
        boolean rebuild = levelChanged || texture == null || originChunkX != nextX || originChunkZ != nextZ;
        originChunkX = nextX;
        originChunkZ = nextZ;
        if (texture == null) {
            texture = new DynamicTexture(SIZE, SIZE, false);
            texture.setFilter(false, false);
        }
        if (rebuild) tiles.keySet().removeIf(key -> !inWindow(key));

        NativeImage pixels = texture.getPixels();
        int newTiles = 0;
        Tile oldest = null;
        boolean changed = rebuild;
        for (ChunkPos offset : SAMPLE_ORDER) {
            int x = anchorX + offset.x;
            int z = anchorZ + offset.z;
            long key = ChunkPos.asLong(x, z);
            LevelChunk chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
            Tile cached = tiles.get(key);
            if (cached != null && cached.chunk != chunk) {
                tiles.remove(key);
                if (!rebuild) blit(pixels, x, z, null);
                changed = true;
                cached = null;
            }
            if (chunk == null) continue;
            if (cached == null && newTiles < NEW_TILES_PER_TICK) {
                cached = sample(chunk);
                tiles.put(key, cached);
                if (!rebuild) blit(pixels, x, z, cached);
                newTiles++;
                changed = true;
            } else if (cached != null && ticks - cached.sampledAt >= REFRESH_AFTER_TICKS
                    && (oldest == null || cached.sampledAt < oldest.sampledAt)) {
                oldest = cached;
            }
        }
        if (oldest != null) {
            Tile refreshed = sample(oldest.chunk);
            ChunkPos pos = refreshed.chunk.getPos();
            tiles.put(pos.toLong(), refreshed);
            if (!rebuild && !Arrays.equals(oldest.colors, refreshed.colors)) {
                blit(pixels, pos.x, pos.z, refreshed);
                changed = true;
            }
        }
        if (rebuild) {
            for (ChunkPos offset : SAMPLE_ORDER) {
                int x = anchorX + offset.x;
                int z = anchorZ + offset.z;
                blit(pixels, x, z, tiles.get(ChunkPos.asLong(x, z)));
            }
        }
        if (changed) {
            texture.upload();
            revision++;
        }
    }

    private static int anchorChunk(double coordinate) {
        int step = CHUNK_SIZE * ORIGIN_STEP_CHUNKS;
        return (int) Math.floor((coordinate + step / 2.0) / step) * ORIGIN_STEP_CHUNKS;
    }

    private boolean inWindow(long key) {
        int x = ChunkPos.getX(key) - originChunkX;
        int z = ChunkPos.getZ(key) - originChunkZ;
        return x >= 0 && z >= 0 && x < 2 * CHUNK_RADIUS && z < 2 * CHUNK_RADIUS;
    }

    private Tile sample(LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        int[] colors = new int[CHUNK_SIZE * CHUNK_SIZE];
        BlockPos.MutableBlockPos block = new BlockPos.MutableBlockPos();
        Heightmap.Types surface = Heightmap.Types.WORLD_SURFACE;
        for (int z = 0; z < CHUNK_SIZE; z++) {
            for (int x = 0; x < CHUNK_SIZE; x++) {
                int height = chunk.getHeight(surface, x, z);
                int northHeight = chunk.getHeight(surface, x, Math.max(0, z - 1));
                MapColor.Brightness light = height > northHeight ? MapColor.Brightness.HIGH
                        : height < northHeight ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                MapColor color = MapColor.NONE;
                for (int y = height; y >= level.getMinBuildHeight() && color == MapColor.NONE; y--) {
                    block.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
                    color = chunk.getBlockState(block).getMapColor(level, block);
                }
                colors[z * CHUNK_SIZE + x] = color == MapColor.NONE ? BACKGROUND_ABGR : color.calculateRGBColor(light);
            }
        }
        return new Tile(chunk, colors, ticks);
    }

    private void blit(NativeImage pixels, int chunkX, int chunkZ, Tile tile) {
        int offsetX = (chunkX - originChunkX) * CHUNK_SIZE;
        int offsetZ = (chunkZ - originChunkZ) * CHUNK_SIZE;
        for (int z = 0; z < CHUNK_SIZE; z++) {
            for (int x = 0; x < CHUNK_SIZE; x++) {
                pixels.setPixelRGBA(offsetX + x, offsetZ + z, tile == null ? BACKGROUND_ABGR : tile.colors[z * CHUNK_SIZE + x]);
            }
        }
    }

    private static List<ChunkPos> sampleOrder() {
        List<ChunkPos> offsets = new ArrayList<>();
        for (int z = -CHUNK_RADIUS; z < CHUNK_RADIUS; z++) {
            for (int x = -CHUNK_RADIUS; x < CHUNK_RADIUS; x++) offsets.add(new ChunkPos(x, z));
        }
        offsets.sort(Comparator.comparingInt(pos -> pos.x * pos.x + pos.z * pos.z));
        return List.copyOf(offsets);
    }

    @Override
    public void close() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
        tiles.clear();
        level = null;
    }
}
