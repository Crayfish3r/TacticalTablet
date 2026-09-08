package com.makar.tacticaltablet.game.lobby;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

import java.util.ArrayList;
import java.util.List;

record LobbyChunkRange(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
    static LobbyChunkRange fromStructure(BlockPos origin, Vec3i size, int marginChunks) {
        if (origin == null || size == null) throw new IllegalArgumentException("origin and size are required");
        return fromStructure(
                origin.getX(), origin.getZ(), size.getX(), size.getZ(), marginChunks);
    }

    static LobbyChunkRange fromStructure(
            int originX,
            int originZ,
            int sizeX,
            int sizeZ,
            int marginChunks
    ) {
        if (sizeX <= 0 || sizeZ <= 0) throw new IllegalArgumentException("structure size must be positive");
        if (marginChunks < 0) throw new IllegalArgumentException("chunk margin must not be negative");

        int minChunkX = Math.floorDiv(originX, 16) - marginChunks;
        int maxChunkX = Math.floorDiv(originX + sizeX - 1, 16) + marginChunks;
        int minChunkZ = Math.floorDiv(originZ, 16) - marginChunks;
        int maxChunkZ = Math.floorDiv(originZ + sizeZ - 1, 16) + marginChunks;
        return new LobbyChunkRange(minChunkX, maxChunkX, minChunkZ, maxChunkZ);
    }

    int chunkCount() {
        return (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
    }

    List<ChunkCoordinate> positions() {
        List<ChunkCoordinate> positions = new ArrayList<>(chunkCount());
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                positions.add(new ChunkCoordinate(x, z));
            }
        }
        return List.copyOf(positions);
    }

    record ChunkCoordinate(int x, int z) {
    }
}
