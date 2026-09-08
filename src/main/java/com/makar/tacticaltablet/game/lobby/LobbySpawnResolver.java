package com.makar.tacticaltablet.game.lobby;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.GameStateManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

final class LobbySpawnResolver {
    static final int CANONICAL_X = 0;
    static final int CANONICAL_Y = 69;
    static final int CANONICAL_Z = 0;
    private static final int HORIZONTAL_SEARCH_RADIUS = 8;
    private static final int VERTICAL_SEARCH_RADIUS = 4;
    private static final double PLAYER_HALF_WIDTH = 0.3D;
    private static final double PLAYER_HEIGHT = 1.8D;
    private static final double SUPPORT_PROBE_DEPTH = 0.125D;

    private LobbySpawnResolver() {
    }

    static Optional<Vec3> resolve(ServerLevel lobby, ServerPlayer player) {
        if (lobby == null || player == null
                || !lobby.dimension().equals(GameStateManager.LOBBY_DIMENSION)) {
            TacticalTabletMod.LOGGER.error("Cannot resolve lobby spawn: lobby:lobby is unavailable or mismatched");
            return Optional.empty();
        }

        Optional<StructureTemplate> template = lobby.getStructureManager()
                .get(LobbyBootstrapManager.LOBBY_SPAWN_TEMPLATE);
        if (template.isEmpty()) {
            TacticalTabletMod.LOGGER.error(
                    "Cannot resolve lobby spawn for player {} ({}): template lobby:spawn is unavailable",
                    player.getGameProfile().getName(), player.getUUID());
            return Optional.empty();
        }

        Vec3i size = template.orElseThrow().getSize();
        BlockPos origin = LobbyBootstrapManager.LOBBY_SPAWN_ORIGIN;
        LobbySpawnSearch.Bounds bounds = new LobbySpawnSearch.Bounds(
                origin.getX(), origin.getX() + size.getX() - 1,
                origin.getY(), origin.getY() + size.getY(),
                origin.getZ(), origin.getZ() + size.getZ() - 1
        );
        LobbySpawnSearch.Position canonical = new LobbySpawnSearch.Position(
                CANONICAL_X, CANONICAL_Y, CANONICAL_Z);
        WorldCollisionProbe probe = new WorldCollisionProbe(lobby, player);
        LobbySpawnSearch.Validation canonicalValidation = LobbySpawnSearch.validate(canonical, probe);
        if (canonicalValidation.valid()) return Optional.of(toWorldPosition(canonical));

        Optional<LobbySpawnSearch.Position> fallback = LobbySpawnSearch.findNearest(
                canonical,
                bounds,
                HORIZONTAL_SEARCH_RADIUS,
                VERTICAL_SEARCH_RADIUS,
                probe
        );
        if (fallback.isPresent()) {
            Vec3 resolved = toWorldPosition(fallback.orElseThrow());
            TacticalTabletMod.LOGGER.warn(
                    "Canonical lobby spawn ({}, {}, {}) is invalid for player {} ({}) reason={}; using fallback ({}, {}, {})",
                    CANONICAL_X + 0.5D, CANONICAL_Y, CANONICAL_Z + 0.5D,
                    player.getGameProfile().getName(), player.getUUID(), canonicalValidation.failure(),
                    resolved.x, resolved.y, resolved.z
            );
            return Optional.of(resolved);
        }

        TacticalTabletMod.LOGGER.error(
                "Safe lobby spawn is completely unavailable for player {} ({}); canonical=({}, {}, {}), reason={}, searchRadius={}x{}",
                player.getGameProfile().getName(), player.getUUID(),
                CANONICAL_X + 0.5D, CANONICAL_Y, CANONICAL_Z + 0.5D,
                canonicalValidation.failure(), HORIZONTAL_SEARCH_RADIUS, VERTICAL_SEARCH_RADIUS
        );
        return Optional.empty();
    }

    private static Vec3 toWorldPosition(LobbySpawnSearch.Position position) {
        return new Vec3(position.x() + 0.5D, position.y(), position.z() + 0.5D);
    }

    private static final class WorldCollisionProbe implements LobbySpawnSearch.CollisionProbe {
        private final ServerLevel lobby;
        private final ServerPlayer player;
        private final Set<Long> availableChunks = new HashSet<>();

        private WorldCollisionProbe(ServerLevel lobby, ServerPlayer player) {
            this.lobby = lobby;
            this.player = player;
        }

        @Override
        public boolean chunkAvailable(LobbySpawnSearch.Position position) {
            int chunkX = Math.floorDiv(position.x(), 16);
            int chunkZ = Math.floorDiv(position.z(), 16);
            long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
            if (availableChunks.contains(key)) return true;
            try {
                lobby.getChunk(chunkX, chunkZ);
                availableChunks.add(key);
                return true;
            } catch (RuntimeException exception) {
                return false;
            }
        }

        @Override
        public boolean hasSupportingCollision(LobbySpawnSearch.Position position) {
            double centerX = position.x() + 0.5D;
            double centerZ = position.z() + 0.5D;
            AABB supportProbe = new AABB(
                    centerX - PLAYER_HALF_WIDTH,
                    position.y() - SUPPORT_PROBE_DEPTH,
                    centerZ - PLAYER_HALF_WIDTH,
                    centerX + PLAYER_HALF_WIDTH,
                    position.y(),
                    centerZ + PLAYER_HALF_WIDTH
            );
            return lobby.getBlockCollisions(player, supportProbe).iterator().hasNext();
        }

        @Override
        public boolean bodyIntersectsCollision(LobbySpawnSearch.Position position) {
            double centerX = position.x() + 0.5D;
            double centerZ = position.z() + 0.5D;
            AABB playerBody = new AABB(
                    centerX - PLAYER_HALF_WIDTH,
                    position.y(),
                    centerZ - PLAYER_HALF_WIDTH,
                    centerX + PLAYER_HALF_WIDTH,
                    position.y() + PLAYER_HEIGHT,
                    centerZ + PLAYER_HALF_WIDTH
            );
            return lobby.getBlockCollisions(player, playerBody).iterator().hasNext();
        }
    }
}
