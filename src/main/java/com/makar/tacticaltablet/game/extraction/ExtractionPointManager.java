package com.makar.tacticaltablet.game.extraction;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.game.lives.LivesManager;
import com.makar.tacticaltablet.game.team.TeamId;
import com.makar.tacticaltablet.game.team.TeamMatchManager;
import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.progression.XpNotifier;
import com.makar.tacticaltablet.tablet.PlayerTabletState;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class ExtractionPointManager {
    private static final Random RANDOM = new Random();
    private static ExtractionPointData data = ExtractionPointData.idle();
    private static ExtractionPointConfig config = new ExtractionPointConfig();
    private static long matchStartTick = 0L;
    private static int particleTicker = 0;
    private static double decayRemainderTicks = 0.0D;
    private static Boolean forcedContested = null;
    private static UUID forcedOwnerPlayerId = null;
    private static ExtractionPointVisualHelper.VisualMode debugVisualMode = null;

    private ExtractionPointManager() {
    }

    public static void onMatchStart(MinecraftServer server) {
        reset(server);
        config = ExtractionPointConfig.load(server);
        matchStartTick = now(server);
        if (!config.enabled || LivesManager.getAlivePlayerCount(server) < config.minAlivePlayers) {
            return;
        }

        int delaySeconds = config.startDelayMinSeconds;
        int range = config.startDelayMaxSeconds - config.startDelayMinSeconds;
        if (range > 0) {
            delaySeconds += RANDOM.nextInt(range + 1);
        }

        data = new ExtractionPointData();
        data.eventId = UUID.randomUUID();
        data.state = ExtractionPointState.SCHEDULED;
        data.scheduledStartTick = now(server) + delaySeconds * 20L;
        data.expireAtMatchTick = matchStartTick + config.expireAtMatchTimeSeconds * 20L;
        data.radius = config.captureRadius;
        data.halfHeight = effectiveCaptureHalfHeight();
        data.requiredCaptureTicks = config.requiredCaptureSeconds * 20;
        data.nextMilestoneRewardAtTicks = config.milestoneRewardIntervalSeconds * 20;
        TacticalTabletMod.LOGGER.info("ExtractionPoint scheduled: delay={}s, eventId={}", delaySeconds, data.eventId);
    }

    public static void tick(MinecraftServer server) {
        if (server == null || data == null || data.state == ExtractionPointState.IDLE) return;
        if (!GameStateManager.isRunning(server) || GameStateManager.getMatchPhase() != MatchPhase.RUNNING) return;

        long tick = now(server);
        if (data.state == ExtractionPointState.SCHEDULED) {
            if (tick >= data.scheduledStartTick) {
                activateRandom(server);
            }
            return;
        }

        if (data.state == ExtractionPointState.ACTIVE) {
            tickActive(server);
            return;
        }

        if (data.state == ExtractionPointState.ENDING_WINNER || data.state == ExtractionPointState.ENDING_EXPIRED) {
            tickEnding(server);
            if (tick >= data.endingUntilTick) {
                cleanup(server);
            }
        }
    }

    public static void reset(MinecraftServer server) {
        cleanup(server);
        config = ExtractionPointConfig.load(server);
        data = ExtractionPointData.idle();
        forcedContested = null;
        forcedOwnerPlayerId = null;
        debugVisualMode = null;
        particleTicker = 0;
        decayRemainderTicks = 0.0D;
    }

    public static void cleanup(MinecraftServer server) {
        if (data != null && data.bossbar != null) {
            data.bossbar.removeAllPlayers();
            data.bossbar.setVisible(false);
            data.bossbar = null;
        }
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                performCleanup(player);
            }
        }
        if (data != null) {
            data.state = ExtractionPointState.IDLE;
            data.playersInside.clear();
            data.teamsInside.clear();
        }
    }

    public static void onPlayerDeathOrLogout(ServerPlayer player) {
        if (player == null) return;
        performCleanup(player);
        if (data == null) return;
        UUID uuid = player.getUUID();
        data.playersInside.remove(uuid);
        if (uuid.equals(data.currentOwnerPlayerId)) {
            data.currentOwnerPlayerId = null;
            data.continuousOwnerCaptureTicks = 0;
        }
        if (uuid.equals(forcedOwnerPlayerId)) {
            forcedOwnerPlayerId = null;
        }
    }

    public static void onPlayerRespawn(ServerPlayer player) {
        if (player == null) return;
        performCleanup(player);
        giveCompassToActiveParticipant(player);
    }

    public static void giveCompassToActiveParticipant(ServerPlayer player) {
        if (player == null || data == null || data.state != ExtractionPointState.ACTIVE || !config.navigatorEnabled) return;
        if (isEligibleForExtraction(player)) {
            ExtractionCompassHelper.giveOrUpdate(player, data, Level.OVERWORLD);
        }
    }

    public static boolean isActive() {
        return data != null && data.state == ExtractionPointState.ACTIVE;
    }

    public static ExtractionPointData getData() {
        return data;
    }

    public static ExtractionPointConfig getConfig(MinecraftServer server) {
        if (server != null) {
            config = ExtractionPointConfig.load(server);
        }
        return config;
    }

    public static long getMatchTimeSeconds(MinecraftServer server) {
        if (server == null || matchStartTick <= 0L) return 0L;
        return Math.max(0L, (now(server) - matchStartTick) / 20L);
    }

    public static boolean startRandom(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        config = ExtractionPointConfig.load(server);
        matchStartTick = matchStartTick <= 0L ? now(server) : matchStartTick;
        data = new ExtractionPointData();
        data.eventId = UUID.randomUUID();
        data.radius = config.captureRadius;
        data.halfHeight = effectiveCaptureHalfHeight();
        data.requiredCaptureTicks = config.requiredCaptureSeconds * 20;
        data.nextMilestoneRewardAtTicks = config.milestoneRewardIntervalSeconds * 20;
        data.expireAtMatchTick = matchStartTick + config.expireAtMatchTimeSeconds * 20L;
        return activateRandom(server);
    }

    public static boolean startAt(CommandSourceStack source, BlockPos pos) {
        MinecraftServer server = source.getServer();
        ServerLevel level = GameStateManager.getOverworld(server);
        if (server == null || level == null || pos == null) return false;
        config = ExtractionPointConfig.load(server);
        matchStartTick = matchStartTick <= 0L ? now(server) : matchStartTick;
        data = createActiveData(server, pos);
        startActive(server, level);
        return true;
    }

    public static void stopExpired(MinecraftServer server) {
        finishExpired(server);
    }

    public static void setProgressSeconds(int seconds) {
        if (data == null) return;
        data.globalCaptureProgressTicks = clamp(seconds * 20, 0, Math.max(1, data.requiredCaptureTicks));
    }

    public static void addProgressSeconds(int seconds) {
        if (data == null) return;
        data.globalCaptureProgressTicks = clamp(data.globalCaptureProgressTicks + seconds * 20, 0, Math.max(1, data.requiredCaptureTicks));
    }

    public static void resetProgress() {
        if (data == null) return;
        data.globalCaptureProgressTicks = 0;
        data.continuousOwnerCaptureTicks = 0;
        data.nextMilestoneRewardAtTicks = config.milestoneRewardIntervalSeconds * 20;
    }

    public static void decayProgressSeconds(int seconds) {
        if (data == null) return;
        data.globalCaptureProgressTicks = Math.max(0, data.globalCaptureProgressTicks - seconds * 20);
    }

    public static void setForcedContested(Boolean value) {
        forcedContested = value;
    }

    public static void forceOwner(ServerPlayer player) {
        forcedOwnerPlayerId = player == null ? null : player.getUUID();
        if (player != null && data != null) {
            data.currentOwnerPlayerId = player.getUUID();
            data.currentOwnerTeamId = null;
        }
    }

    public static void clearOwner() {
        forcedOwnerPlayerId = null;
        if (data != null) {
            data.currentOwnerPlayerId = null;
            data.currentOwnerTeamId = null;
            data.continuousOwnerCaptureTicks = 0;
        }
    }

    public static void setDebugVisualMode(ExtractionPointVisualHelper.VisualMode mode) {
        debugVisualMode = mode;
    }

    public static void rewardMilestone(ServerPlayer player) {
        if (player != null) {
            reward(player, config.milestoneClassXp, config.milestoneCoins, false, false);
        }
    }

    public static void rewardFinal(ServerPlayer player) {
        if (player != null) {
            reward(player, config.finalClassXp, config.finalCoins, true, false);
        }
    }

    public static FindPositionResult findPosition(MinecraftServer server, int attempts) {
        ServerLevel level = GameStateManager.getOverworld(server);
        FindPositionResult result = new FindPositionResult();
        if (level == null) return result;
        WorldBorder border = level.getWorldBorder();
        int maxAttempts = Math.max(1, attempts);
        for (int index = 0; index < maxAttempts; index++) {
            BlockPos candidate = randomCandidate(level, border);
            Rejection rejection = validateCandidate(level, candidate, border);
            result.count(rejection);
            if (rejection == Rejection.ACCEPTED) {
                result.acceptedPos = candidate;
                break;
            }
        }
        return result;
    }

    public static double distanceToNearestBorderSide(ServerLevel level, BlockPos center) {
        if (level == null || center == null) return 0.0D;
        WorldBorder border = level.getWorldBorder();
        double halfSize = border.getSize() / 2.0D;
        double dx = Math.abs((center.getX() + 0.5D) - border.getCenterX());
        double dz = Math.abs((center.getZ() + 0.5D) - border.getCenterZ());
        return halfSize - Math.max(dx, dz);
    }

    public static boolean willExpireByBorder(ServerLevel level) {
        if (data == null || data.center == null) return false;
        return distanceToNearestBorderSide(level, data.center) <= data.radius + config.borderSafetyMargin;
    }

    private static boolean activateRandom(MinecraftServer server) {
        ServerLevel level = GameStateManager.getOverworld(server);
        if (level == null) return false;
        FindPositionResult result = findPosition(server, config.maxLocationAttempts);
        if (result.acceptedPos == null) {
            finishExpired(server);
            return false;
        }

        data = createActiveData(server, result.acceptedPos);
        startActive(server, level);
        return true;
    }

    private static ExtractionPointData createActiveData(MinecraftServer server, BlockPos center) {
        ExtractionPointData active = new ExtractionPointData();
        active.eventId = UUID.randomUUID();
        active.center = center;
        active.radius = config.captureRadius;
        active.halfHeight = effectiveCaptureHalfHeight();
        active.state = ExtractionPointState.ACTIVE;
        active.activeStartTick = now(server);
        active.expireAtMatchTick = matchStartTick + config.expireAtMatchTimeSeconds * 20L;
        active.requiredCaptureTicks = config.requiredCaptureSeconds * 20;
        active.nextMilestoneRewardAtTicks = config.milestoneRewardIntervalSeconds * 20;
        return active;
    }

    private static void startActive(MinecraftServer server, ServerLevel level) {
        ensureBossbar(server);
        syncBossbarPlayers(server);
        if (config.navigatorEnabled) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (isEligibleForExtraction(player)) {
                    ExtractionCompassHelper.giveOrUpdate(player, data, level.dimension());
                }
            }
        }
        broadcast(server, "[" + config.displayName + "] Сигнал активен. Найдите и удерживайте зону.");
        TacticalTabletMod.LOGGER.info("ExtractionPoint active: center={}, eventId={}", data.center, data.eventId);
    }

    private static void tickActive(MinecraftServer server) {
        ServerLevel level = GameStateManager.getOverworld(server);
        if (level == null || data.center == null) return;
        if (now(server) >= data.expireAtMatchTick || willExpireByBorder(level)) {
            finishExpired(server);
            return;
        }

        updatePlayersInside(server);
        updateCapture(server);
        updateBossbar(server);
        tickParticles(level);

        if (data.globalCaptureProgressTicks >= data.requiredCaptureTicks) {
            finishWinner(server, level);
        }
    }

    private static void tickEnding(MinecraftServer server) {
        syncBossbarPlayers(server);
        ServerLevel level = GameStateManager.getOverworld(server);
        if (level != null) {
            tickParticles(level);
        }
    }

    private static void updatePlayersInside(MinecraftServer server) {
        data.playersInside.clear();
        data.teamsInside.clear();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isEligibleForExtraction(player) || !isInside(player)) continue;
            data.playersInside.add(player.getUUID());
            TeamId team = TeamMatchManager.getTeam(player);
            if (team != null) {
                data.teamsInside.add(team.name());
            }
        }
    }

    private static void updateCapture(MinecraftServer server) {
        if (Boolean.TRUE.equals(forcedContested)) {
            markContested();
            return;
        }

        ServerPlayer forcedOwner = forcedOwnerPlayerId == null ? null : server.getPlayerList().getPlayer(forcedOwnerPlayerId);
        if (forcedOwner != null && isEligibleForExtraction(forcedOwner)) {
            captureByPlayer(server, forcedOwner);
            return;
        }

        boolean teamMode = GameStateManager.getCurrentMode().isTeamMode();
        if (data.playersInside.isEmpty()) {
            markEmpty();
            return;
        }

        if (teamMode) {
            if (data.teamsInside.size() != 1) {
                markContested();
                return;
            }
            captureByTeam(server, data.teamsInside.iterator().next());
            return;
        }

        if (data.playersInside.size() != 1) {
            markContested();
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(data.playersInside.iterator().next());
        if (owner == null) {
            markEmpty();
            return;
        }
        captureByPlayer(server, owner);
    }

    private static void captureByPlayer(MinecraftServer server, ServerPlayer owner) {
        boolean sameOwner = owner.getUUID().equals(data.currentOwnerPlayerId) && data.currentOwnerTeamId == null;
        if (!sameOwner) {
            data.currentOwnerPlayerId = owner.getUUID();
            data.currentOwnerTeamId = null;
            data.continuousOwnerCaptureTicks = 0;
            data.nextMilestoneRewardAtTicks = config.milestoneRewardIntervalSeconds * 20;
        }
        data.contested = false;
        data.globalCaptureProgressTicks = Math.min(data.requiredCaptureTicks, data.globalCaptureProgressTicks + 1);
        data.continuousOwnerCaptureTicks++;
        maybeRewardMilestone(List.of(owner));
    }

    private static void captureByTeam(MinecraftServer server, String teamName) {
        boolean sameOwner = teamName.equals(data.currentOwnerTeamId);
        if (!sameOwner) {
            data.currentOwnerPlayerId = null;
            data.currentOwnerTeamId = teamName;
            data.continuousOwnerCaptureTicks = 0;
            data.nextMilestoneRewardAtTicks = config.milestoneRewardIntervalSeconds * 20;
        }
        data.contested = false;
        data.globalCaptureProgressTicks = Math.min(data.requiredCaptureTicks, data.globalCaptureProgressTicks + 1);
        data.continuousOwnerCaptureTicks++;
        maybeRewardMilestone(playersInsideForTeam(server, teamName));
    }

    private static void markContested() {
        data.contested = true;
        data.continuousOwnerCaptureTicks = 0;
        data.nextMilestoneRewardAtTicks = config.milestoneRewardIntervalSeconds * 20;
    }

    private static void markEmpty() {
        data.contested = false;
        data.currentOwnerPlayerId = null;
        data.currentOwnerTeamId = null;
        data.continuousOwnerCaptureTicks = 0;
        data.nextMilestoneRewardAtTicks = config.milestoneRewardIntervalSeconds * 20;
        decayRemainderTicks += config.progressDecayPerSecond;
        int decayTicks = (int) decayRemainderTicks;
        decayRemainderTicks -= decayTicks;
        data.globalCaptureProgressTicks = Math.max(0, data.globalCaptureProgressTicks - decayTicks);
    }

    private static void maybeRewardMilestone(List<ServerPlayer> players) {
        if (data.continuousOwnerCaptureTicks < data.nextMilestoneRewardAtTicks) return;
        for (ServerPlayer player : players) {
            reward(player, config.milestoneClassXp, config.milestoneCoins, false, false);
        }
        data.nextMilestoneRewardAtTicks += config.milestoneRewardIntervalSeconds * 20;
    }

    private static void finishWinner(MinecraftServer server, ServerLevel level) {
        List<ServerPlayer> winners = finalRewardPlayers(server);
        for (ServerPlayer player : winners) {
            reward(player, config.finalClassXp, config.finalCoins, true, data.currentOwnerTeamId != null);
        }

        data.state = ExtractionPointState.ENDING_WINNER;
        data.endingUntilTick = now(server) + config.winnerBossbarSeconds * 20L;
        updateBossbarWinner(server);
        ExtractionPointVisualHelper.playCaptured(level, data.center);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            performCleanup(player);
        }
    }

    private static void finishExpired(MinecraftServer server) {
        if (server == null || data == null || data.state == ExtractionPointState.IDLE) return;
        data.state = ExtractionPointState.ENDING_EXPIRED;
        data.endingUntilTick = now(server) + config.endingFadeSeconds * 20L;
        ensureBossbar(server);
        data.bossbar.setName(Component.literal("Сигнал бизнес-точки потерян"));
        data.bossbar.setColor(BossEvent.BossBarColor.WHITE);
        data.bossbar.setProgress(Math.max(0.0F, progress()));
        syncBossbarPlayers(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            performCleanup(player);
        }
    }

    private static List<ServerPlayer> finalRewardPlayers(MinecraftServer server) {
        if (data.currentOwnerTeamId != null) {
            return playersInsideForTeam(server, data.currentOwnerTeamId);
        }
        ServerPlayer player = data.currentOwnerPlayerId == null ? null : server.getPlayerList().getPlayer(data.currentOwnerPlayerId);
        if (player != null && isEligibleForExtraction(player)) return List.of(player);
        return List.of();
    }

    private static List<ServerPlayer> playersInsideForTeam(MinecraftServer server, String teamName) {
        List<ServerPlayer> players = new ArrayList<>();
        if (teamName == null) return players;
        for (UUID uuid : data.playersInside) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null || !isEligibleForExtraction(player)) continue;
            TeamId team = TeamMatchManager.getTeam(player);
            if (team != null && team.name().equals(teamName)) {
                players.add(player);
            }
        }
        return players;
    }

    private static void reward(ServerPlayer player, int classXp, int coins, boolean finisher, boolean teamReward) {
        if (player == null) return;
        if (coins > 0) {
            PlayerProgressManager.addCoins(player, coins);
        }
        String clazz = PlayerTabletState.getSelectedClass(player);
        if (clazz != null && !clazz.isBlank() && classXp > 0) {
            classXp = ClassXPManager.addXP(player, clazz, classXp);
            XpNotifier.send(player, classXp, config.displayName);
        } else {
            ClassXPManager.sync(player);
        }
        PlayerProgressManager.savePlayer(player);

        if (finisher) {
            String text = teamReward
                    ? "[" + config.displayName + "] Ваша команда захватила зону: +" + classXp + " XP класса, +" + coins + " монет."
                    : "[" + config.displayName + "] Вы захватили зону: +" + classXp + " XP класса, +" + coins + " монет.";
            player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GOLD));
        } else {
            player.connection.send(new ClientboundSetActionBarTextPacket(
                    Component.literal("+" + classXp + " XP класса, +" + coins + " монеты за удержание").withStyle(ChatFormatting.GOLD)
            ));
        }
    }

    private static void ensureBossbar(MinecraftServer server) {
        if (data.bossbar == null) {
            data.bossbar = new ServerBossEvent(
                    Component.literal(config.displayName + " активна"),
                    BossEvent.BossBarColor.YELLOW,
                    BossEvent.BossBarOverlay.PROGRESS
            );
        }
        data.bossbar.setVisible(true);
        syncBossbarPlayers(server);
    }

    private static void syncBossbarPlayers(MinecraftServer server) {
        if (server == null || data == null || data.bossbar == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isEligibleForExtraction(player)) {
                data.bossbar.addPlayer(player);
            } else {
                data.bossbar.removePlayer(player);
            }
        }
    }

    private static void updateBossbar(MinecraftServer server) {
        ensureBossbar(server);
        data.bossbar.setProgress(progress());
        if (data.contested) {
            data.bossbar.setName(Component.literal("Зона оспаривается"));
            data.bossbar.setColor(BossEvent.BossBarColor.RED);
            return;
        }
        if (data.playersInside.isEmpty()) {
            data.bossbar.setName(Component.literal("бизнес-точка свободна — прогресс снижается"));
            data.bossbar.setColor(BossEvent.BossBarColor.YELLOW);
            return;
        }
        int progressSeconds = data.globalCaptureProgressTicks / 20;
        int requiredSeconds = Math.max(1, data.requiredCaptureTicks / 20);
        if (data.currentOwnerTeamId != null) {
            data.bossbar.setName(Component.literal("Захват: Команда " + data.currentOwnerTeamId + " — " + progressSeconds + "/" + requiredSeconds + " сек"));
        } else {
            ServerPlayer owner = data.currentOwnerPlayerId == null ? null : server.getPlayerList().getPlayer(data.currentOwnerPlayerId);
            String name = owner == null ? "-" : owner.getName().getString();
            data.bossbar.setName(Component.literal("Захват: " + name + " — " + progressSeconds + "/" + requiredSeconds + " сек"));
        }
        data.bossbar.setColor(BossEvent.BossBarColor.GREEN);
    }

    private static void updateBossbarWinner(MinecraftServer server) {
        ensureBossbar(server);
        data.bossbar.setColor(BossEvent.BossBarColor.GREEN);
        data.bossbar.setProgress(1.0F);
        if (data.currentOwnerTeamId != null) {
            data.bossbar.setName(Component.literal("бизнес-точка захвачена: Команда " + data.currentOwnerTeamId));
            return;
        }
        ServerPlayer owner = data.currentOwnerPlayerId == null ? null : server.getPlayerList().getPlayer(data.currentOwnerPlayerId);
        data.bossbar.setName(Component.literal("бизнес-точка захвачена: " + (owner == null ? "-" : owner.getName().getString())));
    }

    private static void tickParticles(ServerLevel level) {
        if (++particleTicker < config.particleUpdateIntervalTicks) return;
        particleTicker = 0;
        ExtractionPointVisualHelper.VisualMode mode = debugVisualMode;
        if (mode == null) {
            if (data.state == ExtractionPointState.ENDING_EXPIRED) {
                mode = ExtractionPointVisualHelper.VisualMode.ENDING;
            } else if (data.state == ExtractionPointState.ENDING_WINNER) {
                mode = ExtractionPointVisualHelper.VisualMode.CAPTURED;
            } else if (data.contested) {
                mode = ExtractionPointVisualHelper.VisualMode.CONTESTED;
            } else if (!data.playersInside.isEmpty()) {
                mode = ExtractionPointVisualHelper.VisualMode.CAPTURING;
            } else {
                mode = ExtractionPointVisualHelper.VisualMode.NORMAL;
            }
        }
        ExtractionPointVisualHelper.spawnRing(level, data, config, mode);
    }

    private static BlockPos randomCandidate(ServerLevel level, WorldBorder border) {
        double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
        double distance = RANDOM.nextDouble() * config.centerOffsetRadius;
        int x = (int) Math.round(border.getCenterX() + Math.cos(angle) * distance);
        int z = (int) Math.round(border.getCenterZ() + Math.sin(angle) * distance);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    private static Rejection validateCandidate(ServerLevel level, BlockPos pos, WorldBorder border) {
        if (pos.getY() < config.minEventY) return Rejection.REJECTED_Y_TOO_LOW;
        if (pos.getY() > config.maxEventY) return Rejection.REJECTED_Y_TOO_HIGH;
        if (distanceToNearestBorderSide(level, pos) <= config.captureRadius + config.borderSafetyMargin) {
            return Rejection.REJECTED_BORDER;
        }

        BlockState at = level.getBlockState(pos);
        BlockState below = level.getBlockState(pos.below());
        if (config.blockedLiquids) {
            if (at.getFluidState().is(FluidTags.WATER) || below.getFluidState().is(FluidTags.WATER)) return Rejection.REJECTED_WATER;
            if (at.getFluidState().is(FluidTags.LAVA) || below.getFluidState().is(FluidTags.LAVA)) return Rejection.REJECTED_LAVA;
        }
        return Rejection.ACCEPTED;
    }

    private static boolean isEligibleForExtraction(ServerPlayer player) {
        return player != null
                && player.isAlive()
                && player.level().dimension().equals(Level.OVERWORLD)
                && player.getTags().contains("war.playing")
                && LivesManager.isAliveParticipant(player)
                && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR;
    }

    private static void performCleanup(ServerPlayer player) {
        ExtractionCompassHelper.removeAllExtractionCompasses(player);
    }

    private static boolean isInside(ServerPlayer player) {
        Vec3 pos = player.position();
        double dx = pos.x - (data.center.getX() + 0.5D);
        double dz = pos.z - (data.center.getZ() + 0.5D);
        double horizontalSq = dx * dx + dz * dz;
        return horizontalSq <= data.radius * data.radius
                && Math.abs(pos.y - data.center.getY()) <= data.halfHeight;
    }

    private static double effectiveCaptureHalfHeight() {
        return Math.max(24.0D, config.captureHalfHeight);
    }

    private static float progress() {
        if (data == null || data.requiredCaptureTicks <= 0) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F, data.globalCaptureProgressTicks / (float) data.requiredCaptureTicks));
    }

    private static long now(MinecraftServer server) {
        return server == null ? 0L : server.getTickCount();
    }

    private static void broadcast(MinecraftServer server, String message) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum Rejection {
        REJECTED_WATER,
        REJECTED_LAVA,
        REJECTED_Y_TOO_LOW,
        REJECTED_Y_TOO_HIGH,
        REJECTED_BORDER,
        ACCEPTED
    }

    public static class FindPositionResult {
        public int rejectedWater;
        public int rejectedLava;
        public int rejectedYTooLow;
        public int rejectedYTooHigh;
        public int rejectedBorder;
        public int accepted;
        public BlockPos acceptedPos;

        private void count(Rejection rejection) {
            switch (rejection) {
                case REJECTED_WATER -> rejectedWater++;
                case REJECTED_LAVA -> rejectedLava++;
                case REJECTED_Y_TOO_LOW -> rejectedYTooLow++;
                case REJECTED_Y_TOO_HIGH -> rejectedYTooHigh++;
                case REJECTED_BORDER -> rejectedBorder++;
                case ACCEPTED -> accepted++;
            }
        }
    }
}
