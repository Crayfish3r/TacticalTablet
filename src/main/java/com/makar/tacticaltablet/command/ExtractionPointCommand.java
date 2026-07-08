package com.makar.tacticaltablet.command;

import com.makar.tacticaltablet.game.extraction.ExtractionCompassHelper;
import com.makar.tacticaltablet.game.extraction.ExtractionPointData;
import com.makar.tacticaltablet.game.extraction.ExtractionPointManager;
import com.makar.tacticaltablet.game.extraction.ExtractionPointVisualHelper;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;

public final class ExtractionPointCommand {
    private ExtractionPointCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tt")
                .then(Commands.literal("extraction")
                        .then(Commands.literal("status")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> status(context.getSource())))
                        .then(Commands.literal("tp")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> teleport(context.getSource(), false))
                                .then(Commands.literal("edge")
                                        .executes(context -> teleport(context.getSource(), true))))
                        .then(Commands.literal("start")
                                .requires(source -> source.hasPermission(4))
                                .executes(context -> start(context.getSource()))
                                .then(Commands.literal("here")
                                        .executes(context -> startHere(context.getSource())))
                                .then(Commands.literal("at")
                                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                                .executes(context -> startAt(
                                                                        context.getSource(),
                                                                        DoubleArgumentType.getDouble(context, "x"),
                                                                        DoubleArgumentType.getDouble(context, "y"),
                                                                        DoubleArgumentType.getDouble(context, "z")
                                                                )))))))
                        .then(Commands.literal("stop")
                                .requires(source -> source.hasPermission(4) && debugEnabled(source))
                                .executes(context -> stop(context.getSource())))
                        .then(Commands.literal("cleanup")
                                .requires(source -> source.hasPermission(4) && debugEnabled(source))
                                .executes(context -> cleanup(context.getSource())))
                        .then(Commands.literal("progress")
                                .requires(source -> source.hasPermission(4) && debugEnabled(source))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                                .executes(context -> progressSet(context.getSource(), IntegerArgumentType.getInteger(context, "seconds")))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer())
                                                .executes(context -> progressAdd(context.getSource(), IntegerArgumentType.getInteger(context, "seconds")))))
                                .then(Commands.literal("reset")
                                        .executes(context -> progressReset(context.getSource())))
                                .then(Commands.literal("decay")
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                                .executes(context -> progressDecay(context.getSource(), IntegerArgumentType.getInteger(context, "seconds"))))))
                        .then(Commands.literal("debug")
                                .requires(source -> source.hasPermission(4) && debugEnabled(source))
                                .then(Commands.literal("visual")
                                        .then(Commands.literal("normal").executes(context -> visual(context.getSource(), ExtractionPointVisualHelper.VisualMode.NORMAL)))
                                        .then(Commands.literal("capturing").executes(context -> visual(context.getSource(), ExtractionPointVisualHelper.VisualMode.CAPTURING)))
                                        .then(Commands.literal("contested").executes(context -> visual(context.getSource(), ExtractionPointVisualHelper.VisualMode.CONTESTED)))
                                        .then(Commands.literal("captured").executes(context -> visual(context.getSource(), ExtractionPointVisualHelper.VisualMode.CAPTURED)))
                                        .then(Commands.literal("ending").executes(context -> visual(context.getSource(), ExtractionPointVisualHelper.VisualMode.ENDING))))
                                .then(Commands.literal("forceContested")
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(context -> forceContested(context.getSource(), BoolArgumentType.getBool(context, "value")))))
                                .then(Commands.literal("forceOwner")
                                        .then(Commands.literal("self")
                                                .executes(context -> forceOwnerSelf(context.getSource()))))
                                .then(Commands.literal("clearOwner")
                                        .executes(context -> clearOwner(context.getSource()))))
                        .then(Commands.literal("compass")
                                .requires(source -> source.hasPermission(4) && debugEnabled(source))
                                .then(Commands.literal("give")
                                        .executes(context -> compassGiveSelf(context.getSource()))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> compassGive(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                                .then(Commands.literal("remove")
                                        .executes(context -> compassRemoveSelf(context.getSource())))
                                .then(Commands.literal("removeAll")
                                        .executes(context -> compassRemoveAll(context.getSource()))))
                        .then(Commands.literal("reward")
                                .requires(source -> source.hasPermission(4) && debugEnabled(source))
                                .then(Commands.literal("milestone")
                                        .executes(context -> rewardMilestone(context.getSource())))
                                .then(Commands.literal("final")
                                        .executes(context -> rewardFinalSelf(context.getSource()))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> rewardFinal(context.getSource(), EntityArgument.getPlayer(context, "player"))))))
                        .then(Commands.literal("bordercheck")
                                .requires(source -> source.hasPermission(4) && debugEnabled(source))
                                .executes(context -> borderCheck(context.getSource())))
                        .then(Commands.literal("findpos")
                                .requires(source -> source.hasPermission(4) && debugEnabled(source))
                                .executes(context -> findPos(context.getSource(), -1))
                                .then(Commands.argument("attempts", IntegerArgumentType.integer(1))
                                        .executes(context -> findPos(context.getSource(), IntegerArgumentType.getInteger(context, "attempts")))))
                )
        );
    }

    private static boolean debugEnabled(CommandSourceStack source) {
        return ExtractionPointManager.getConfig(source.getServer()).debugCommandsEnabled;
    }

    private static int start(CommandSourceStack source) {
        boolean started = ExtractionPointManager.startRandom(source);
        if (!started) {
            source.sendFailure(Component.literal("[ExtractionPoint] Не удалось найти валидную позицию."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[ExtractionPoint] Event запущен около центра border."), true);
        return 1;
    }

    private static int startHere(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return startAt(source, player.getX(), player.getY(), player.getZ());
    }

    private static int startAt(CommandSourceStack source, double x, double y, double z) {
        boolean started = ExtractionPointManager.startAt(source, BlockPos.containing(x, y, z));
        if (!started) {
            source.sendFailure(Component.literal("[ExtractionPoint] Не удалось запустить event."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[ExtractionPoint] Event запущен: " + format(BlockPos.containing(x, y, z))), true);
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        ExtractionPointManager.stopExpired(source.getServer());
        source.sendSuccess(() -> Component.literal("[ExtractionPoint] Event завершен без победителя."), true);
        return 1;
    }

    private static int cleanup(CommandSourceStack source) {
        ExtractionPointManager.reset(source.getServer());
        source.sendSuccess(() -> Component.literal("[ExtractionPoint] State очищен."), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        ExtractionPointData data = ExtractionPointManager.getData();
        ServerLevel level = source.getServer().overworld();
        source.sendSuccess(() -> Component.literal("[ExtractionPoint] state=" + data.state), false);
        source.sendSuccess(() -> Component.literal("- eventId=" + value(data.eventId)), false);
        source.sendSuccess(() -> Component.literal("- center=" + format(data.center)), false);
        source.sendSuccess(() -> Component.literal("- radius=" + data.radius + ", halfHeight=" + data.halfHeight), false);
        source.sendSuccess(() -> Component.literal("- globalProgressSeconds=" + data.globalCaptureProgressTicks / 20
                + ", requiredCaptureSeconds=" + Math.max(1, data.requiredCaptureTicks / 20)), false);
        source.sendSuccess(() -> Component.literal("- currentOwnerPlayer=" + value(data.currentOwnerPlayerId)
                + ", currentOwnerTeam=" + value(data.currentOwnerTeamId)), false);
        source.sendSuccess(() -> Component.literal("- continuousOwnerSeconds=" + data.continuousOwnerCaptureTicks / 20
                + ", contested=" + data.contested), false);
        source.sendSuccess(() -> Component.literal("- playersInside=" + data.playersInside), false);
        source.sendSuccess(() -> Component.literal("- teamsInside=" + data.teamsInside), false);
        source.sendSuccess(() -> Component.literal("- matchTime=" + ExtractionPointManager.getMatchTimeSeconds(source.getServer())
                + ", timeUntilExpire=" + Math.max(0L, (data.expireAtMatchTick - source.getServer().getTickCount()) / 20L)), false);
        source.sendSuccess(() -> Component.literal("- distanceToNearestBorderSide="
                + String.format(java.util.Locale.ROOT, "%.2f", ExtractionPointManager.distanceToNearestBorderSide(level, data.center))), false);
        return 1;
    }

    private static int teleport(CommandSourceStack source, boolean edge) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ExtractionPointData data = ExtractionPointManager.getData();
        if (data.center == null) {
            source.sendFailure(Component.literal("[ExtractionPoint] Активной точки нет."));
            return 0;
        }
        double x = data.center.getX() + 0.5D + (edge ? data.radius : 0.0D);
        double y = data.center.getY() + 1.0D;
        double z = data.center.getZ() + 0.5D;
        player.teleportTo(source.getLevel(), x, y, z, player.getYRot(), player.getXRot());
        return 1;
    }

    private static int progressSet(CommandSourceStack source, int seconds) {
        ExtractionPointManager.setProgressSeconds(seconds);
        source.sendSuccess(() -> Component.literal("[ExtractionPoint] Progress set: " + seconds + "s"), true);
        return 1;
    }

    private static int progressAdd(CommandSourceStack source, int seconds) {
        ExtractionPointManager.addProgressSeconds(seconds);
        source.sendSuccess(() -> Component.literal("[ExtractionPoint] Progress add: " + seconds + "s"), true);
        return 1;
    }

    private static int progressReset(CommandSourceStack source) {
        ExtractionPointManager.resetProgress();
        source.sendSuccess(() -> Component.literal("[ExtractionPoint] Progress reset."), true);
        return 1;
    }

    private static int progressDecay(CommandSourceStack source, int seconds) {
        ExtractionPointManager.decayProgressSeconds(seconds);
        source.sendSuccess(() -> Component.literal("[ExtractionPoint] Progress decayed: " + seconds + "s"), true);
        return 1;
    }

    private static int visual(CommandSourceStack source, ExtractionPointVisualHelper.VisualMode mode) {
        ExtractionPointManager.setDebugVisualMode(mode);
        source.sendSuccess(() -> Component.literal("[ExtractionPoint] Debug visual=" + mode), true);
        return 1;
    }

    private static int forceContested(CommandSourceStack source, boolean value) {
        ExtractionPointManager.setForcedContested(value);
        source.sendSuccess(() -> Component.literal("[ExtractionPoint] forceContested=" + value), true);
        return 1;
    }

    private static int forceOwnerSelf(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ExtractionPointManager.forceOwner(source.getPlayerOrException());
        source.sendSuccess(() -> Component.literal("[ExtractionPoint] Owner forced to self."), true);
        return 1;
    }

    private static int clearOwner(CommandSourceStack source) {
        ExtractionPointManager.clearOwner();
        ExtractionPointManager.setForcedContested(null);
        source.sendSuccess(() -> Component.literal("[ExtractionPoint] Debug owner/contested cleared."), true);
        return 1;
    }

    private static int compassGiveSelf(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return compassGive(source, source.getPlayerOrException());
    }

    private static int compassGive(CommandSourceStack source, ServerPlayer player) {
        ExtractionCompassHelper.giveOrUpdate(player, ExtractionPointManager.getData(), source.getLevel().dimension());
        source.sendSuccess(() -> Component.literal("[ExtractionPoint] Compass given: " + player.getName().getString()), true);
        return 1;
    }

    private static int compassRemoveSelf(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ExtractionCompassHelper.removeAllExtractionCompasses(source.getPlayerOrException());
        source.sendSuccess(() -> Component.literal("[ExtractionPoint] Compass removed."), true);
        return 1;
    }

    private static int compassRemoveAll(CommandSourceStack source) {
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            ExtractionCompassHelper.removeAllExtractionCompasses(player);
        }
        source.sendSuccess(() -> Component.literal("[ExtractionPoint] All compasses removed."), true);
        return 1;
    }

    private static int rewardMilestone(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ExtractionPointManager.rewardMilestone(source.getPlayerOrException());
        return 1;
    }

    private static int rewardFinalSelf(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return rewardFinal(source, source.getPlayerOrException());
    }

    private static int rewardFinal(CommandSourceStack source, ServerPlayer player) {
        ExtractionPointManager.rewardFinal(player);
        source.sendSuccess(() -> Component.literal("[ExtractionPoint] Final reward issued: " + player.getName().getString()), true);
        return 1;
    }

    private static int borderCheck(CommandSourceStack source) {
        ExtractionPointData data = ExtractionPointManager.getData();
        ServerLevel level = source.getServer().overworld();
        WorldBorder border = level.getWorldBorder();
        source.sendSuccess(() -> Component.literal("[ExtractionPoint] borderSize=" + border.getSize()), false);
        source.sendSuccess(() -> Component.literal("- borderCenter=" + border.getCenterX() + " " + border.getCenterZ()), false);
        source.sendSuccess(() -> Component.literal("- eventCenter=" + format(data.center)), false);
        source.sendSuccess(() -> Component.literal("- captureRadius=" + data.radius
                + ", safetyMargin=" + ExtractionPointManager.getConfig(source.getServer()).borderSafetyMargin), false);
        source.sendSuccess(() -> Component.literal("- distanceToNearestBorderSide="
                + String.format(java.util.Locale.ROOT, "%.2f", ExtractionPointManager.distanceToNearestBorderSide(level, data.center))), false);
        source.sendSuccess(() -> Component.literal("- willExpireByBorder=" + ExtractionPointManager.willExpireByBorder(level)), false);
        return 1;
    }

    private static int findPos(CommandSourceStack source, int attempts) {
        int actualAttempts = attempts > 0 ? attempts : ExtractionPointManager.getConfig(source.getServer()).maxLocationAttempts;
        ExtractionPointManager.FindPositionResult result = ExtractionPointManager.findPosition(source.getServer(), actualAttempts);
        source.sendSuccess(() -> Component.literal("[ExtractionPoint] findpos attempts=" + actualAttempts), false);
        source.sendSuccess(() -> Component.literal("- rejected_water=" + result.rejectedWater), false);
        source.sendSuccess(() -> Component.literal("- rejected_lava=" + result.rejectedLava), false);
        source.sendSuccess(() -> Component.literal("- rejected_y_too_low=" + result.rejectedYTooLow), false);
        source.sendSuccess(() -> Component.literal("- rejected_y_too_high=" + result.rejectedYTooHigh), false);
        source.sendSuccess(() -> Component.literal("- rejected_border=" + result.rejectedBorder), false);
        source.sendSuccess(() -> Component.literal("- accepted=" + result.accepted + ", pos=" + format(result.acceptedPos)), false);
        return result.accepted > 0 ? 1 : 0;
    }

    private static String format(BlockPos pos) {
        if (pos == null) return "-";
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static String value(Object value) {
        return value == null ? "-" : value.toString();
    }
}
