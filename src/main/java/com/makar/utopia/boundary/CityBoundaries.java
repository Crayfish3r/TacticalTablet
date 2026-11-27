package com.makar.utopia.boundary;

import com.makar.utopia.Utopia;
import com.makar.utopia.network.MapNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread ownership of the city rules and connected-player state. */
@Mod.EventBusSubscriber(modid = Utopia.MOD_ID)
public final class CityBoundaries {
    private static final LevelResource SERVER_CONFIG_DIRECTORY = new LevelResource("serverconfig");
    private static final Map<UUID, Presence> PLAYERS = new HashMap<>();
    private static LesetskConfig config;
    private static ResourceKey<Level> cityDimension;
    private static MapNetwork.Geometry geometry;

    private static final class Presence {
        private Vec3 lastSafe;
        private boolean insideHill;
        private boolean tabletPending;
    }

    private CityBoundaries() { }

    @SubscribeEvent
    public static void start(ServerStartingEvent event) {
        LesetskConfig loaded = LesetskConfig.load(event.getServer().getWorldPath(SERVER_CONFIG_DIRECTORY));
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, loaded.dimension());
        if (event.getServer().getLevel(dimension) == null) {
            throw new IllegalStateException("Utopia dimension " + loaded.dimension() + " does not exist in this world");
        }
        config = loaded;
        cityDimension = dimension;
        geometry = new MapNetwork.Geometry(loaded.dimension(), loaded.perimeter(), loaded.hill());
        Utopia.LOGGER.info("Utopia city boundaries loaded for {}", loaded.dimension());
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        PLAYERS.clear();
        config = null;
        cityDimension = null;
        geometry = null;
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Presence presence = new Presence();
        if (!enforce(player, presence)) return;
        PLAYERS.put(player.getUUID(), presence);
        presence.tabletPending = true;
        grantTablet(player, presence);
        presence.insideHill = insideHill(player);
        MapNetwork.sendGeometry(player, geometry, presence.insideHill);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Presence presence = PLAYERS.get(player.getUUID());
        if (presence == null || !enforce(player, presence)) return;
        presence.tabletPending = true;
        grantTablet(player, presence);
        syncHill(player, presence);
    }

    @SubscribeEvent
    public static void dimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Presence presence = PLAYERS.get(player.getUUID());
        if (presence != null && enforce(player, presence)) syncHill(player, presence);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PLAYERS.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || !player.isAlive()) return;
        Presence presence = PLAYERS.get(player.getUUID());
        if (presence == null || !enforce(player, presence)) return;
        grantTablet(player, presence);
        syncHill(player, presence);
    }

    private static boolean enforce(ServerPlayer player, Presence presence) {
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(cityDimension)) return true;
        Vec3 position = player.position();
        if (insideCity(position)) {
            if (player.onGround() && !player.isPassenger() && !position.equals(presence.lastSafe)
                    && isSafe(level, player, position, false)) {
                presence.lastSafe = position;
            }
            return true;
        }

        Vec3 destination = presence.lastSafe;
        if (destination == null || !isSafe(level, player, destination, true)) {
            destination = fallback(level, player);
        }
        if (destination == null) {
            Utopia.LOGGER.error("No safe Utopia return point for {} at {} in {}. Check {} or world spawn.",
                    player.getGameProfile().getName(), position, cityDimension.location(), LesetskConfig.FILE_NAME);
            player.connection.disconnect(Component.translatable("disconnect.utopia.no_safe_return"));
            return false;
        }

        player.stopRiding();
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        player.connection.teleport(destination.x, destination.y, destination.z, player.getYRot(), player.getXRot());
        player.connection.resetPosition();
        presence.lastSafe = destination;
        return true;
    }

    private static Vec3 fallback(ServerLevel level, ServerPlayer player) {
        LesetskConfig.ReturnPoint point = config.returnPoint();
        BlockPos column = BlockPos.containing(point.x(), 0, point.z());
        double y = point.y() != null ? point.y()
                : level.getChunkAt(column).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        column.getX() & 15, column.getZ() & 15) + 1;
        Vec3 configured = new Vec3(point.x(), y, point.z());
        if (isSafe(level, player, configured, true)) return configured;

        Vec3 spawn = Vec3.atBottomCenterOf(level.getSharedSpawnPos());
        return insideCity(spawn) && isSafe(level, player, spawn, true) ? spawn : null;
    }

    private static boolean isSafe(ServerLevel level, ServerPlayer player, Vec3 point, boolean loadChunks) {
        AABB body = player.getDimensions(Pose.STANDING).makeBoundingBox(point);
        if (body.minY <= level.getMinBuildHeight() || body.maxY > level.getMaxBuildHeight()) return false;
        BlockPos min = BlockPos.containing(body.minX, body.minY, body.minZ);
        BlockPos max = BlockPos.containing(body.maxX, body.maxY, body.maxZ);
        if (loadChunks) {
            for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
                for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) level.getChunk(x, z);
            }
        } else if (!level.hasChunksAt(min, max)) {
            return false;
        }

        BlockPos below = BlockPos.containing(point.x, point.y - 0.08, point.z);
        BlockState support = level.getBlockState(below);
        if (support.is(Blocks.MAGMA_BLOCK) || support.is(Blocks.CACTUS) || support.is(Blocks.POWDER_SNOW)
                || support.is(BlockTags.CAMPFIRES)) return false;
        if (!level.noCollision(player, body) || level.containsAnyLiquid(body)
                || level.getBlockState(BlockPos.containing(point)).is(BlockTags.FIRE)) return false;

        // A short probe under the centre checks actual support, including the heights of slabs and stairs.
        AABB feet = new AABB(point.x - 0.1, point.y - 0.08, point.z - 0.1,
                point.x + 0.1, point.y, point.z + 0.1);
        return level.getBlockCollisions(player, feet).iterator().hasNext();
    }

    private static boolean insideCity(Vec3 position) {
        return config.perimeter().contains(position.x, position.z);
    }

    private static boolean insideHill(ServerPlayer player) {
        return player.level().dimension().equals(cityDimension) && config.hill().contains(player.getX(), player.getZ());
    }

    private static void syncHill(ServerPlayer player, Presence presence) {
        boolean inside = insideHill(player);
        if (inside != presence.insideHill) {
            presence.insideHill = inside;
            MapNetwork.sendHillStatus(player, inside);
        }
    }

    private static void grantTablet(ServerPlayer player, Presence presence) {
        if (!presence.tabletPending || !player.isAlive()) return;
        Inventory inventory = player.getInventory();
        if (player.getOffhandItem().is(Utopia.TABLET.get())
                || inventory.items.stream().anyMatch(stack -> stack.is(Utopia.TABLET.get()))) {
            presence.tabletPending = false;
            return;
        }
        int freeSlot = inventory.getFreeSlot();
        if (freeSlot >= 0) {
            inventory.setItem(freeSlot, new ItemStack(Utopia.TABLET.get()));
            inventory.setChanged();
            presence.tabletPending = false;
        }
    }
}
