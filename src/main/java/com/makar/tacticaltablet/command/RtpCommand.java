package com.makar.tacticaltablet.command;

import com.makar.tacticaltablet.game.teleport.SafeTeleport;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.stream.Collectors;

public class RtpCommand {

    private static final int DEFAULT_TEST_POINTS = 50;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ttrtp")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("testpoints")
                        .executes(context -> testPoints(context.getSource(), DEFAULT_TEST_POINTS))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 500))
                                .executes(context -> testPoints(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "count")
                                ))
                        )
                )
        );
    }

    private static int testPoints(CommandSourceStack source, int count) {
        SafeTeleport.TestResult result = SafeTeleport.testPoints(source.getServer(), count);

        source.sendSuccess(() -> Component.literal("[WAR] Тест точек RTP:"), false);
        source.sendSuccess(() -> Component.literal("- запрошено: " + result.requested()), false);
        source.sendSuccess(() -> Component.literal("- найдено безопасных: " + result.valid()), false);
        source.sendSuccess(() -> Component.literal("- попыток: " + result.attempts()), false);
        source.sendSuccess(() -> Component.literal("- размер границы: " + format(result.borderSize())), false);
        source.sendSuccess(() -> Component.literal("- отступ от границы: " + format(result.margin())), false);
        source.sendSuccess(() -> Component.literal("- точек в пуле сейчас: " + SafeTeleport.getPreparedCount()), false);

        if (!result.samples().isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("- примеры точек: " + result.samples().stream()
                            .map(RtpCommand::format)
                            .collect(Collectors.joining(", "))),
                    false
            );
        }

        if (result.valid() < result.requested()) {
            source.sendFailure(Component.literal("[WAR] RTP не нашёл достаточно безопасных точек. Проверь границу мира и поверхность карты."));
        }

        return result.valid();
    }

    private static String format(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
