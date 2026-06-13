package com.makar.tacticaltablet.airdrop;

import com.makar.tacticaltablet.airdrop.loot.AirdropLootGenerator;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public final class AirdropCommands {

    private AirdropCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ttairdrop")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start")
                        .executes(context -> start(context.getSource(), false)))
                .then(Commands.literal("start_now")
                        .executes(context -> start(context.getSource(), true)))
                .then(Commands.literal("cancel")
                        .executes(context -> cancel(context.getSource())))
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource(), false)))
                .then(Commands.literal("debug")
                        .executes(context -> status(context.getSource(), true)))
                .then(Commands.literal("reload_loot")
                        .executes(context -> reloadLoot(context.getSource())))
        );
    }

    private static int start(CommandSourceStack source, boolean instant) {
        ServerLevel level = source.getLevel();
        if (AirdropManager.hasActiveAirdrop()) {
            source.sendFailure(Component.literal("[СБРОС] Сброс уже активен."));
            return 0;
        }

        AirdropManager.start(level, instant);
        source.sendSuccess(() -> Component.literal("[СБРОС] Запуск запрошен."), true);
        return 1;
    }

    private static int cancel(CommandSourceStack source) {
        if (!AirdropManager.hasActiveAirdrop()) {
            source.sendFailure(Component.literal("[СБРОС] Активного сброса нет."));
            return 0;
        }

        AirdropManager.cancel(source.getLevel());
        source.sendSuccess(() -> Component.literal("[СБРОС] Сброс отменён."), true);
        return 1;
    }

    private static int status(CommandSourceStack source, boolean debug) {
        AirdropData data = AirdropManager.getActiveAirdrop();

        if (data == null) {
            source.sendSuccess(() -> Component.literal("[СБРОС] состояние=нет"), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("[СБРОС] состояние=" + stateName(data.state)), false);
        source.sendSuccess(() -> Component.literal("- реальная точка: " + format(data.realDropPos)), false);
        source.sendSuccess(() -> Component.literal("- точка компаса: " + format(data.compassTargetPos)), false);
        source.sendSuccess(() -> Component.literal("- тиков до сброса: " + data.ticksUntilDrop), false);
        source.sendSuccess(() -> Component.literal("- тиков после приземления: " + data.ticksSinceLanded), false);

        if (debug) {
            source.sendSuccess(() -> Component.literal("- ID: " + data.id), false);
            source.sendSuccess(() -> Component.literal("- измерение: " + data.dimension.location()), false);
            source.sendSuccess(() -> Component.literal("- открыл: " + (data.openedBy == null ? "-" : data.openedBy)), false);
            source.sendSuccess(() -> Component.literal("- позиция сундука: " + format(data.chestPos)), false);
            source.sendSuccess(() -> Component.literal("- тиков после открытия: " + data.ticksSinceOpened), false);
            source.sendSuccess(() -> Component.literal("- зелёный дым: " + data.greenSmoke), false);
            source.sendSuccess(() -> Component.literal("- текущая высота ящика: " + String.format(java.util.Locale.ROOT, "%.2f", data.currentCrateY)), false);
        }

        return 1;
    }

    private static int reloadLoot(CommandSourceStack source) {
        int count = AirdropLootGenerator.reloadLoot();

        source.sendSuccess(
                () -> Component.literal("[СБРОС] Конфиг лута перезагружен. Валидных записей: " + count),
                true
        );
        return count;
    }

    private static String stateName(AirdropState state) {
        return switch (state) {
            case NONE -> "нет";
            case ANNOUNCED -> "объявлен";
            case FALLING -> "падает";
            case LANDED -> "приземлился";
            case OPENED -> "открыт";
            case EXPIRED -> "завершён";
        };
    }

    private static String format(BlockPos pos) {
        if (pos == null) return "-";
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
