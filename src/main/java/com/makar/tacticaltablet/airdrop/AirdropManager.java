package com.makar.tacticaltablet.airdrop;

import com.makar.tacticaltablet.airdrop.loot.AirdropLootGenerator;
import com.makar.tacticaltablet.airdrop.net.AirdropNoticePacket;
import com.makar.tacticaltablet.airdrop.net.AirdropSmokeStatePacket;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.core.ModBlocks;
import com.makar.tacticaltablet.core.ModItems;
import com.makar.tacticaltablet.core.ModSounds;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.integration.discord.DiscordLeaderboardService;
import com.makar.tacticaltablet.tablet.net.PacketHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.UUID;

public final class AirdropManager {

    private static final int FIRST_AUTO_SPAWN_DELAY_TICKS = 2 * 60 * 20;
    private static final int AUTO_SPAWN_INTERVAL_TICKS = 5 * 60 * 20;
    private static final int ANNOUNCE_DELAY_TICKS = 60 * 20;
    private static final int OPENED_DURATION_TICKS = 120 * 20;
    private static final int LANDED_EXPIRE_TICKS = 300 * 20;
    private static final int FIND_ATTEMPTS = 50;
    private static final double FALL_START_HEIGHT = 100.0D;
    private static final double FALL_SPEED_PER_TICK = 0.15D;
    private static final double MIN_RADIUS_FACTOR = 0.18D;
    private static final double MAX_RADIUS_FACTOR = 0.75D;
    private static final boolean REMOVE_CHEST_ON_EXPIRE = false;
    private static final int AIRDROP_NOTICE_DURATION_TICKS = 5 * 20;
    private static final int AIRDROP_NOTICE_COLOR = 0xFFFF5555;

    private static AirdropData activeAirdrop;
    private static int autoSpawnTicker = 0;
    private static int nextAutoSpawnDelayTicks = FIRST_AUTO_SPAWN_DELAY_TICKS;

    private AirdropManager() {
    }

    public static boolean hasActiveAirdrop() {
        return activeAirdrop != null && activeAirdrop.state != AirdropState.NONE && activeAirdrop.state != AirdropState.EXPIRED;
    }

    public static AirdropData getActiveAirdrop() {
        return activeAirdrop;
    }

    public static void start(ServerLevel level, boolean instant) {
        if (level == null) return;

        if (hasActiveAirdrop()) {
            TacticalTabletMod.LOGGER.warn("Tactical Tablet AirDrop start skipped: another AirDrop is already active.");
            return;
        }

        if (level.dimension().equals(GameStateManager.LOBBY_DIMENSION)) {
            TacticalTabletMod.LOGGER.warn("Tactical Tablet AirDrop start skipped: cannot start in lobby dimension.");
            return;
        }

        BlockPos realDropPos = findSafeDropPos(level);
        if (realDropPos == null) {
            TacticalTabletMod.LOGGER.warn("Tactical Tablet AirDrop start cancelled: no safe drop point found.");
            return;
        }

        BlockPos compassTarget = createCompassTarget(level, realDropPos);
        activeAirdrop = new AirdropData(
                UUID.randomUUID(),
                instant ? AirdropState.FALLING : AirdropState.ANNOUNCED,
                level.dimension(),
                realDropPos,
                compassTarget,
                instant ? 0 : ANNOUNCE_DELAY_TICKS,
                realDropPos.getY() + FALL_START_HEIGHT
        );

        if (!instant) {
            announceStart(level);
        }
        giveOrUpdateCompasses(level);
        if (GameStateManager.isRunning(level.getServer())) {
            DiscordLeaderboardService.recordMatchAirdropStarted();
        }

        if (instant) {
            broadcast(level, "§c[СБРОС] §fГруз уже в пути.");
            sendAirdropNotice(level, "AirDrop падает!", AirdropNoticePacket.NoticeType.DROPPING);
            spawnFallingCrate(level);
        }

        TacticalTabletMod.LOGGER.info(
                "Tactical Tablet AirDrop started: id={}, state={}, real={}, compass={}",
                activeAirdrop.id,
                activeAirdrop.state,
                formatPos(activeAirdrop.realDropPos),
                formatPos(activeAirdrop.compassTargetPos)
        );
    }

    public static void cancel(ServerLevel level) {
        if (activeAirdrop == null) return;
        ServerLevel activeLevel = resolveActiveLevel(level);
        finish(activeLevel == null ? level : activeLevel);
    }

    public static void serverTick(ServerLevel level) {
        if (level == null) return;
        tickAutoSpawner(level);

        if (activeAirdrop == null) return;
        if (!level.dimension().equals(activeAirdrop.dimension)) return;

        switch (activeAirdrop.state) {
            case ANNOUNCED -> tickAnnounced(level);
            case FALLING -> tickFalling(level);
            case LANDED -> tickLanded(level);
            case OPENED -> tickOpened(level);
            default -> {
            }
        }
    }

    public static void onChestInteract(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null || activeAirdrop == null) return;
        if (activeAirdrop.state != AirdropState.LANDED) return;
        if (activeAirdrop.chestPos == null || !activeAirdrop.chestPos.equals(pos)) return;

        activeAirdrop.state = AirdropState.OPENED;
        activeAirdrop.opened = true;
        activeAirdrop.greenSmoke = true;
        activeAirdrop.openedBy = player.getUUID();
        activeAirdrop.ticksSinceOpened = 0;

        TacticalTabletMod.LOGGER.info(
                "Tactical Tablet AirDrop opened: id={}, player={}, chest={}",
                activeAirdrop.id,
                player.getScoreboardName(),
                formatPos(activeAirdrop.chestPos)
        );
    }

    public static boolean isAirdropChest(BlockPos pos) {
        return activeAirdrop != null
                && activeAirdrop.chestPos != null
                && activeAirdrop.chestPos.equals(pos)
                && (activeAirdrop.state == AirdropState.LANDED || activeAirdrop.state == AirdropState.OPENED);
    }

    public static boolean isOrphanedVisualEntity(Entity entity) {
        if (!(entity instanceof net.minecraft.world.entity.decoration.ArmorStand stand)) return false;
        if (!stand.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.AIRDROP_CRATE_FLYING.get())) return false;

        return activeAirdrop == null
                || activeAirdrop.state != AirdropState.FALLING
                || activeAirdrop.visualEntityId == null
                || !activeAirdrop.visualEntityId.equals(stand.getUUID());
    }

    public static void finish(ServerLevel level) {
        if (activeAirdrop == null) return;

        activeAirdrop.state = AirdropState.EXPIRED;

        ServerLevel activeLevel = resolveActiveLevel(level);
        if (activeLevel != null) {
            sendSmokeState(activeLevel, false);
            removeVisualEntity(activeLevel);

            if (REMOVE_CHEST_ON_EXPIRE && activeAirdrop.chestPos != null) {
                activeLevel.removeBlock(activeAirdrop.chestPos, false);
            }

            removeAirdropCompasses(activeLevel);
            broadcast(activeLevel, "§7[СБРОС] Событие завершено.");
        }

        TacticalTabletMod.LOGGER.info("Tactical Tablet AirDrop finished.");
        activeAirdrop = null;
    }

    public static void giveCompassToJoiningPlayer(ServerPlayer player) {
        if (player == null || activeAirdrop == null) return;

        if (activeAirdrop.chestPos != null
                && (activeAirdrop.state == AirdropState.LANDED || activeAirdrop.state == AirdropState.OPENED)) {
            PacketHandler.sendToPlayer(player, createSmokePacket(true));
        }

        if (!isEligibleForCompass(player)) return;

        if (activeAirdrop.state == AirdropState.ANNOUNCED
                || activeAirdrop.state == AirdropState.FALLING
                || activeAirdrop.state == AirdropState.LANDED
                || activeAirdrop.state == AirdropState.OPENED) {
            AirdropCompassHelper.giveOrUpdate(player, activeAirdrop);
        }
    }

    public static void resetAutoScheduler() {
        autoSpawnTicker = 0;
        nextAutoSpawnDelayTicks = FIRST_AUTO_SPAWN_DELAY_TICKS;
    }

    public static void resetRuntime(ServerLevel level) {
        ServerLevel activeLevel = resolveActiveLevel(level);
        if (activeLevel != null) {
            sendSmokeState(activeLevel, false);
            removeVisualEntity(activeLevel);
            removeAirdropCompasses(activeLevel);
        }

        activeAirdrop = null;
        autoSpawnTicker = 0;
        nextAutoSpawnDelayTicks = FIRST_AUTO_SPAWN_DELAY_TICKS;
    }

    private static void tickAutoSpawner(ServerLevel level) {
        if (!GameStateManager.isRunning(level.getServer())) {
            autoSpawnTicker = 0;
            return;
        }

        if (hasActiveAirdrop()) {
            return;
        }

        autoSpawnTicker++;
        if (autoSpawnTicker < nextAutoSpawnDelayTicks) {
            return;
        }

        autoSpawnTicker = 0;
        nextAutoSpawnDelayTicks = AUTO_SPAWN_INTERVAL_TICKS;
        start(level, false);
    }

    private static void tickAnnounced(ServerLevel level) {
        activeAirdrop.ticksUntilDrop--;

        if (activeAirdrop.ticksUntilDrop == 30 * 20) {
            sendAirdropNotice(level, "AirDrop через 30 секунд!", AirdropNoticePacket.NoticeType.COUNTDOWN_30);
            broadcast(level, "§c[СБРОС] §fДо сброса: 30 сек.");
        }

        if (activeAirdrop.ticksUntilDrop == 10 * 20) {
            sendActionBar(level, "§c[СБРОС] §fГруз приближается.");
        }

        if (activeAirdrop.ticksUntilDrop <= 0) {
            activeAirdrop.state = AirdropState.FALLING;
            sendAirdropNotice(level, "AirDrop падает!", AirdropNoticePacket.NoticeType.DROPPING);
            spawnFallingCrate(level);
        }
    }

    private static void tickFalling(ServerLevel level) {
        Entity visual = getVisualEntity(level);
        if (visual == null) {
            spawnFallingCrate(level);
            visual = getVisualEntity(level);
        }

        activeAirdrop.currentCrateY -= FALL_SPEED_PER_TICK;
        double x = activeAirdrop.realDropPos.getX() + 0.5D;
        double y = activeAirdrop.currentCrateY;
        double z = activeAirdrop.realDropPos.getZ() + 0.5D;

        if (visual != null) {
            visual.teleportTo(x, y, z);
        }

        if (activeAirdrop.currentCrateY <= activeAirdrop.realDropPos.getY() + 1.0D) {
            landAirdrop(level);
        }
    }

    private static void tickLanded(ServerLevel level) {
        activeAirdrop.ticksSinceLanded++;

        if (activeAirdrop.ticksSinceLanded >= LANDED_EXPIRE_TICKS) {
            finish(level);
        }
    }

    private static void tickOpened(ServerLevel level) {
        activeAirdrop.ticksSinceOpened++;

        if (activeAirdrop.ticksSinceOpened >= OPENED_DURATION_TICKS) {
            finish(level);
        }
    }

    private static BlockPos findSafeDropPos(ServerLevel level) {
        Vec3 center = getSafeZoneCenter(level);
        double radius = getSafeZoneRadius(level);
        if (radius <= 8.0D) return null;

        double minDistance = Math.min(radius * MIN_RADIUS_FACTOR, Math.max(0.0D, radius - 4.0D));
        double maxDistance = Math.max(minDistance + 1.0D, radius * MAX_RADIUS_FACTOR);

        for (int attempt = 0; attempt < FIND_ATTEMPTS; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double distance = minDistance + level.random.nextDouble() * (maxDistance - minDistance);
            int x = (int) Math.round(center.x + Math.cos(angle) * distance);
            int z = (int) Math.round(center.z + Math.sin(angle) * distance);

            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (isSafeDropPosition(level, surface, attempt < 40)) {
                return surface;
            }
        }

        return null;
    }

    private static BlockPos createCompassTarget(ServerLevel level, BlockPos realPos) {
        Vec3 center = getSafeZoneCenter(level);
        double radius = getSafeZoneRadius(level);

        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double offset = 25.0D + level.random.nextDouble() * 25.0D;
            int x = (int) Math.round(realPos.getX() + Math.cos(angle) * offset);
            int z = (int) Math.round(realPos.getZ() + Math.sin(angle) * offset);

            double distanceToCenter = Math.hypot(x - center.x, z - center.z);
            if (distanceToCenter > radius * 0.9D) continue;

            BlockPos target = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (level.getWorldBorder().isWithinBounds(target)) {
                return target;
            }
        }

        return realPos;
    }

    private static Vec3 getSafeZoneCenter(ServerLevel level) {
        WorldBorder border = level.getWorldBorder();
        return new Vec3(border.getCenterX(), 0.0D, border.getCenterZ());
    }

    private static double getSafeZoneRadius(ServerLevel level) {
        return level.getWorldBorder().getSize() / 2.0D;
    }

    private static boolean isSafeDropPosition(ServerLevel level, BlockPos pos, boolean avoidLeaves) {
        if (pos == null) return false;
        if (level.dimension().equals(GameStateManager.LOBBY_DIMENSION)) return false;
        if (!level.getWorldBorder().isWithinBounds(pos)) return false;
        if (!level.isEmptyBlock(pos)) return false;
        if (!level.isEmptyBlock(pos.above())) return false;

        BlockPos belowPos = pos.below();
        BlockState below = level.getBlockState(belowPos);
        BlockState at = level.getBlockState(pos);

        if (!below.isFaceSturdy(level, belowPos, Direction.UP)) return false;
        if (avoidLeaves && below.is(BlockTags.LEAVES)) return false;
        if (below.getFluidState().is(FluidTags.WATER) || below.getFluidState().is(FluidTags.LAVA)) return false;
        if (at.getFluidState().is(FluidTags.WATER) || at.getFluidState().is(FluidTags.LAVA)) return false;

        return true;
    }

    private static void announceStart(ServerLevel level) {
        sendAirdropNotice(level, "AirDrop через 60 секунд!", AirdropNoticePacket.NoticeType.COUNTDOWN_60);
        broadcast(level, "§c[СБРОС] §fКомпас указывает в примерную зону сброса.");
    }

    private static void giveOrUpdateCompasses(ServerLevel level) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (isEligibleForCompass(player)) {
                AirdropCompassHelper.giveOrUpdate(player, activeAirdrop);
            }
        }
    }

    private static boolean isEligibleForCompass(ServerPlayer player) {
        if (player == null) return false;
        if (LivesManager.isEliminated(player)) return false;

        boolean inBattle = player.getTags().contains("war.playing");
        boolean waitingForRtp = player.getTags().contains("in_lobby");

        return (inBattle || waitingForRtp) && LivesManager.canContinueMatch(player);
    }

    private static void removeAirdropCompasses(ServerLevel level) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            AirdropCompassHelper.removeAllAirdropCompasses(player);
        }
    }

    private static void spawnFallingCrate(ServerLevel level) {
        BlockPos visualPos = BlockPos.containing(
                activeAirdrop.realDropPos.getX() + 0.5D,
                activeAirdrop.currentCrateY,
                activeAirdrop.realDropPos.getZ() + 0.5D
        );
        if (!level.isPositionEntityTicking(visualPos)) {
            return;
        }

        removeVisualEntity(level);

        AirdropVisualArmorStand stand = new AirdropVisualArmorStand(
                level,
                activeAirdrop.realDropPos.getX() + 0.5D,
                activeAirdrop.currentCrateY,
                activeAirdrop.realDropPos.getZ() + 0.5D
        );
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.AIRDROP_CRATE_FLYING.get()));

        activeAirdrop.visualEntityId = stand.getUUID();
        if (!level.addFreshEntity(stand)) {
            activeAirdrop.visualEntityId = null;
        }
    }

    private static void landAirdrop(ServerLevel level) {
        removeVisualEntity(level);

        BlockPos chestPos = findChestPlacement(level, activeAirdrop.realDropPos);
        if (chestPos == null) {
            TacticalTabletMod.LOGGER.warn("Tactical Tablet AirDrop could not place chest at {}", formatPos(activeAirdrop.realDropPos));
            finish(level);
            return;
        }

        level.setBlock(chestPos, ModBlocks.AIRDROP_CRATE.get().defaultBlockState(), 3);
        activeAirdrop.chestPos = chestPos;
        activeAirdrop.state = AirdropState.LANDED;
        activeAirdrop.ticksSinceLanded = 0;

        AirdropLootGenerator.fillChest(level, chestPos);
        sendSmokeState(level, true);
        level.playSound(
                null,
                chestPos,
                ModSounds.PARACHUTE_CLOSE.get(),
                SoundSource.BLOCKS,
                1.0F,
                1.0F
        );
        broadcast(level, "§c[СБРОС] §fГруз приземлился. Следуйте по дыму.");
        TacticalTabletMod.LOGGER.info("Tactical Tablet AirDrop landed at {}", formatPos(chestPos));
    }

    private static void sendSmokeState(ServerLevel level, boolean active) {
        if (level == null || activeAirdrop == null || activeAirdrop.dimension == null) return;

        AirdropSmokeStatePacket packet = createSmokePacket(active);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            PacketHandler.sendToPlayer(player, packet);
        }
    }

    private static AirdropSmokeStatePacket createSmokePacket(boolean active) {
        BlockPos smokePos = activeAirdrop.chestPos != null
                ? activeAirdrop.chestPos
                : activeAirdrop.realDropPos;
        return new AirdropSmokeStatePacket(active, activeAirdrop.dimension.location(), smokePos);
    }

    private static void sendAirdropNotice(ServerLevel level, String message, AirdropNoticePacket.NoticeType type) {
        if (level == null || level.getServer() == null) return;

        AirdropNoticePacket packet = new AirdropNoticePacket(
                message,
                AIRDROP_NOTICE_COLOR,
                AIRDROP_NOTICE_DURATION_TICKS,
                type
        );
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            PacketHandler.sendToPlayer(player, packet);
        }
    }

    private static BlockPos findChestPlacement(ServerLevel level, BlockPos origin) {
        BlockPos[] candidates = new BlockPos[]{
                origin,
                origin.above(),
                origin.north(),
                origin.south(),
                origin.east(),
                origin.west()
        };

        for (BlockPos candidate : candidates) {
            if (level.isEmptyBlock(candidate)
                    && level.isEmptyBlock(candidate.above())
                    && level.getBlockState(candidate.below()).isFaceSturdy(level, candidate.below(), Direction.UP)) {
                return candidate;
            }
        }

        return null;
    }

    private static Entity getVisualEntity(ServerLevel level) {
        if (activeAirdrop == null || activeAirdrop.visualEntityId == null) return null;
        return level.getEntity(activeAirdrop.visualEntityId);
    }

    private static void removeVisualEntity(ServerLevel level) {
        Entity visual = getVisualEntity(level);
        if (visual != null) {
            visual.discard();
        }

        if (activeAirdrop != null) {
            activeAirdrop.visualEntityId = null;
        }
    }

    private static ServerLevel resolveActiveLevel(ServerLevel fallback) {
        if (activeAirdrop == null) return fallback;
        if (fallback != null && fallback.getServer() != null) {
            ServerLevel activeLevel = fallback.getServer().getLevel(activeAirdrop.dimension);
            return activeLevel == null ? fallback : activeLevel;
        }
        return fallback;
    }

    private static void broadcast(ServerLevel level, String message) {
        Component component = Component.literal(message);

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(component);
        }
    }

    private static void sendActionBar(ServerLevel level, String message) {
        Component component = Component.literal(message);

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetActionBarTextPacket(component));
        }
    }

    private static String formatPos(BlockPos pos) {
        if (pos == null) return "-";
        return String.format(Locale.ROOT, "%d %d %d", pos.getX(), pos.getY(), pos.getZ());
    }
}
