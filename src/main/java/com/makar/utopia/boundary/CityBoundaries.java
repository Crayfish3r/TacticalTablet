package com.makar.utopia.boundary;

import com.makar.utopia.Utopia;
import com.makar.utopia.network.MapNetwork;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageTypes;
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
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Server-thread ownership of the city rules and connected-player state. */
@Mod.EventBusSubscriber(modid = Utopia.MOD_ID)
public final class CityBoundaries {
    private static final LevelResource SERVER_CONFIG_DIRECTORY = new LevelResource("serverconfig");
    private static final String NEUTRAL_VISIT = "utopiaNeutralVisit";
    private static final Map<UUID, Presence> PLAYERS = new HashMap<>();
    private static final Map<UUID, Integer> OFFLINE_PORT = new HashMap<>();
    private static LesetskConfig config;
    private static ResourceKey<Level> cityDimension;
    private static MapNetwork.Geometry geometry;
    private static long startedAt;
    private static long offlinePortCycle;
    private static boolean runningAdminDamage;

    private record AdminDamage(Command<CommandSourceStack> command) implements Command<CommandSourceStack> {
        @Override
        public int run(CommandContext<CommandSourceStack> execution) throws CommandSyntaxException {
            boolean previous = runningAdminDamage;
            runningAdminDamage = true;
            try {
                return command.run(execution);
            } finally {
                runningAdminDamage = previous;
            }
        }
    }

    private static final class Presence {
        private final ZoneTimers timers;
        private Vec3 lastSafe;
        private boolean tabletPending;
        private CityZone sentZone;
        private long sentCycle;
        private boolean sentPortExhausted;

        private Presence(ZoneTimers timers) {
            this.timers = timers;
        }
    }

    private CityBoundaries() { }

    @SubscribeEvent
    public static void start(ServerStartingEvent event) {
        LesetskConfig loaded = LesetskConfig.load(event.getServer().getWorldPath(SERVER_CONFIG_DIRECTORY));
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, loaded.dimension());
        ServerLevel level = event.getServer().getLevel(dimension);
        if (level == null) {
            throw new IllegalStateException("Utopia dimension " + loaded.dimension() + " does not exist in this world");
        }
        for (BlockPos spot : loaded.portLootSpots()) {
            if (level.isOutsideBuildHeight(spot)) {
                throw new IllegalStateException(LesetskConfig.FILE_NAME + " portLootSpots position is outside the build height of "
                        + loaded.dimension() + ": " + spot.toShortString());
            }
        }
        config = loaded;
        cityDimension = dimension;
        geometry = new MapNetwork.Geometry(loaded.dimension(), loaded.perimeter(), loaded.hill(), loaded.port());
        startedAt = System.nanoTime();
        offlinePortCycle = 0;
        Utopia.LOGGER.info("Utopia city boundaries loaded for {}", loaded.dimension());
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        if (config == null) return;
        long now = clock();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            Presence presence = PLAYERS.get(player.getUUID());
            if (presence != null) {
                presence.timers.observe(now, zoneAt(player));
                saveNeutral(player, presence.timers);
            }
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        PLAYERS.clear();
        OFFLINE_PORT.clear();
        config = null;
        cityDimension = null;
        geometry = null;
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now = clock();
        Integer savedPort = OFFLINE_PORT.remove(player.getUUID());
        Presence presence = new Presence(new ZoneTimers(now,
                savedPort != null ? savedPort : CityZone.PORT_PROTECTION_MILLIS));
        resumeNeutral(player, presence.timers);
        PLAYERS.put(player.getUUID(), presence);
        if (!observeWithBoundary(player, presence, now)) return;
        presence.tabletPending = true;
        grantTablet(player, presence);
        MapNetwork.sendGeometry(player, geometry, new MapNetwork.Status(presence.timers.zone(), presence.timers.portRemaining()));
        markSent(presence);
        notifyNeutral(player, presence.timers);
        saveNeutral(player, presence.timers);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Presence presence = PLAYERS.get(player.getUUID());
        if (presence == null) return;
        long now = clock();
        if (!event.isEndConquered()) presence.timers.observe(now, CityZone.OUTSIDE);
        if (!observeWithBoundary(player, presence, now)) return;
        presence.tabletPending = true;
        grantTablet(player, presence);
        synchronize(player, presence, true);
    }

    @SubscribeEvent
    public static void dimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Presence presence = PLAYERS.get(player.getUUID());
        if (presence != null && observeWithBoundary(player, presence, clock())) synchronize(player, presence, true);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Presence presence = PLAYERS.remove(player.getUUID());
        if (presence == null) return;
        ZoneTimers timers = presence.timers;
        timers.observe(clock(), zoneAt(player));
        if (timers.portRemaining() < CityZone.PORT_PROTECTION_MILLIS) {
            OFFLINE_PORT.put(player.getUUID(), timers.portRemaining());
        }
        saveNeutral(player, timers);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Presence presence = PLAYERS.get(player.getUUID());
        if (presence == null || !observeWithBoundary(player, presence, clock())) return;
        grantTablet(player, presence);
        synchronize(player, presence, false);
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && config != null) clock();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Presence presence = PLAYERS.get(player.getUUID());
        if (presence == null) return;
        presence.timers.observe(clock(), CityZone.OUTSIDE);
        synchronize(player, presence, false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void damage(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || config == null) return;
        Presence presence = PLAYERS.get(player.getUUID());
        CityZone zone = zoneAt(player);
        if (presence != null) {
            presence.timers.observe(clock(), zone);
            synchronize(player, presence, false);
        }
        var source = event.getSource();
        if (runningAdminDamage || source.is(DamageTypes.GENERIC_KILL)) return;

        boolean protectedHere = switch (zone) {
            case OUTSIDE -> false;
            case HILL -> !source.is(DamageTypes.STARVE)
                    && !(source.is(DamageTypes.FALL) && source.getEntity() == null && source.getDirectEntity() == null);
            case PORT -> presence == null || presence.timers.portRemaining() > 0;
            case NEUTRAL -> presence == null || presence.timers.neutralRemaining() > 0;
        };
        if (protectedHere) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void adminDamage(CommandEvent event) {
        // Scope the exception to vanilla /damage's authorized execution. Vanilla resolves its target once.
        var context = event.getParseResults().getContext();
        var root = context.getDispatcher().getRoot().getChild("damage");
        if (root == null) return;
        while (context.getChild() != null) context = context.getChild();
        var command = context.getCommand();
        if (command != null && !(command instanceof AdminDamage) && !context.getNodes().isEmpty()
                && context.getNodes().get(0).getNode() == root) {
            context.withCommand(new AdminDamage(command));
        }
    }

    private static long clock() {
        long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        long cycle = ZoneTimers.cycle(now);
        if (offlinePortCycle != cycle) {
            OFFLINE_PORT.clear();
            offlinePortCycle = cycle;
        }
        return now;
    }

    private static boolean observeWithBoundary(ServerPlayer player, Presence presence, long now) {
        CityZone zone = zoneAt(player);
        presence.timers.observe(now, zone);
        if (player.isAlive() && !enforce(player, presence)) return false;
        // The hard perimeter can move a player straight back into a different zone.
        if (zone == CityZone.OUTSIDE) presence.timers.observe(now, zoneAt(player));
        return true;
    }

    private static void resumeNeutral(ServerPlayer player, ZoneTimers timers) {
        CompoundTag data = player.getPersistentData();
        CompoundTag visit = data.getCompound(NEUTRAL_VISIT);
        if (cityDimension.location().toString().equals(visit.getString("Dimension"))
                && visit.contains("Remaining", Tag.TAG_INT)) {
            timers.resumeNeutral(visit.getInt("Remaining"), visit.getBoolean("NoticePending"));
        } else {
            data.remove(NEUTRAL_VISIT);
        }
    }

    private static void saveNeutral(ServerPlayer player, ZoneTimers timers) {
        CompoundTag data = player.getPersistentData();
        if (timers.zone() != CityZone.NEUTRAL) {
            data.remove(NEUTRAL_VISIT);
            return;
        }
        // Keep one active visit in Forge's player data for normal saves, logout and server shutdown.
        CompoundTag visit = data.getCompound(NEUTRAL_VISIT);
        if (visit.isEmpty()) {
            visit.putString("Dimension", cityDimension.location().toString());
            data.put(NEUTRAL_VISIT, visit);
        }
        visit.putInt("Remaining", timers.neutralRemaining());
        visit.putBoolean("NoticePending", timers.neutralNoticePending());
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

    private static CityZone zoneAt(ServerPlayer player) {
        if (!player.isAlive() || !player.level().dimension().equals(cityDimension) || !insideCity(player.position())) {
            return CityZone.OUTSIDE;
        }
        double x = player.getX();
        double z = player.getZ();
        if (config.hill().contains(x, z)) return CityZone.HILL;
        if (config.port() != null && config.port().contains(x, z)) return CityZone.PORT;
        return CityZone.NEUTRAL;
    }

    private static void synchronize(ServerPlayer player, Presence presence, boolean force) {
        ZoneTimers timers = presence.timers;
        if (force || presence.sentZone != timers.zone() || presence.sentCycle != timers.cycle()
                || presence.sentPortExhausted != (timers.portRemaining() == 0)) {
            MapNetwork.sendStatus(player, new MapNetwork.Status(timers.zone(), timers.portRemaining()));
            markSent(presence);
        }
        notifyNeutral(player, timers);
        saveNeutral(player, timers);
    }

    private static void markSent(Presence presence) {
        presence.sentZone = presence.timers.zone();
        presence.sentCycle = presence.timers.cycle();
        presence.sentPortExhausted = presence.timers.portRemaining() == 0;
    }

    private static void notifyNeutral(ServerPlayer player, ZoneTimers timers) {
        if (timers.consumeNeutralNotice()) player.displayClientMessage(Component.translatable("message.utopia.damage_enabled"), true);
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
