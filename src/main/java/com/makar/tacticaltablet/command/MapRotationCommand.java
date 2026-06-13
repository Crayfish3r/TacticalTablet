package com.makar.tacticaltablet.command;

import com.makar.tacticaltablet.map.MapRotationManager;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;

public class MapRotationCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ttmaprotation")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource()))
                )
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx.getSource()))
                )
                .then(Commands.literal("arm")
                        .executes(ctx -> arm(ctx.getSource()))
                )
                .then(Commands.literal("disarm")
                        .executes(ctx -> disarm(ctx.getSource()))
                )
                .then(Commands.literal("reload")
                        .executes(ctx -> reload(ctx.getSource()))
                )
                .then(Commands.literal("next")
                        .then(Commands.argument("map", StringArgumentType.greedyString())
                                .executes(ctx -> setNextMap(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "map")
                                ))
                        )
                )
                .then(Commands.literal("clear-next")
                        .executes(ctx -> clearNextMap(ctx.getSource()))
                )
        );

        dispatcher.register(Commands.literal("ttmaps")
                .requires(source -> source.hasPermission(2))
                .redirect(dispatcher.getRoot().getChild("ttmaprotation"))
        );
    }

    private static int status(CommandSourceStack source) {
        MapRotationManager.RotationStatus status = MapRotationManager.getStatus(source.getServer());

        source.sendSuccess(() -> Component.literal("Ротация карт тактического планшета:"), false);
        source.sendSuccess(() -> Component.literal("- включена: " + status.enabled()), false);
        source.sendSuccess(() -> Component.literal("- менять при выключении: " + status.rotateEveryShutdown()), false);
        source.sendSuccess(() -> Component.literal("- подготовлена к следующему выключению: " + status.armed()), false);
        source.sendSuccess(() -> Component.literal("- текущая карта: " + emptyToDash(status.currentMap())), false);
        source.sendSuccess(() -> Component.literal("- следующая карта: " + emptyToDash(status.nextMap())), false);
        source.sendSuccess(() -> Component.literal("- папка карт: " + status.mapsRoot()), false);
        source.sendSuccess(() -> Component.literal("- найдено карт: " + status.maps().size()), false);

        if (status.lastError() != null && !status.lastError().isBlank()) {
            source.sendFailure(Component.literal("Последняя ошибка: " + status.lastError()));
        }

        return status.maps().size();
    }

    private static int list(CommandSourceStack source) {
        List<String> maps = MapRotationManager.listMapNames(source.getServer());

        if (maps.isEmpty()) {
            source.sendFailure(Component.literal("В map_pool не найдено подходящих карт."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Доступные карты: " + String.join(", ", maps)), false);
        return maps.size();
    }

    private static int arm(CommandSourceStack source) {
        try {
            MapRotationManager.arm(source.getServer());
            MapRotationManager.RotationStatus status = MapRotationManager.getStatus(source.getServer());

            source.sendSuccess(
                    () -> Component.literal("Ротация карт подготовлена. При следующем корректном выключении будет установлена карта: "
                            + emptyToDash(status.nextMap())),
                    true
            );
            return 1;
        } catch (IOException exception) {
            source.sendFailure(Component.literal("Не удалось подготовить ротацию карт: " + exception.getMessage()));
            return 0;
        }
    }

    private static int disarm(CommandSourceStack source) {
        try {
            MapRotationManager.disarm(source.getServer());
            source.sendSuccess(() -> Component.literal("Ротация карт снята с подготовки."), true);
            return 1;
        } catch (IOException exception) {
            source.sendFailure(Component.literal("Не удалось снять подготовку ротации карт: " + exception.getMessage()));
            return 0;
        }
    }

    private static int reload(CommandSourceStack source) {
        try {
            MapRotationManager.reload(source.getServer());
            source.sendSuccess(() -> Component.literal("Конфиг ротации карт перезагружен."), true);
            return 1;
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal("Не удалось перезагрузить конфиг ротации карт: " + exception.getMessage()));
            return 0;
        }
    }

    private static int setNextMap(CommandSourceStack source, String mapName) {
        try {
            MapRotationManager.setNextMap(source.getServer(), mapName);
            source.sendSuccess(() -> Component.literal("Следующая карта: " + mapName), true);
            return 1;
        } catch (IOException exception) {
            source.sendFailure(Component.literal("Не удалось выбрать следующую карту: " + exception.getMessage()));
            return 0;
        }
    }

    private static int clearNextMap(CommandSourceStack source) {
        try {
            MapRotationManager.clearNextMapOverride(source.getServer());
            source.sendSuccess(() -> Component.literal("Принудительный выбор следующей карты очищен."), true);
            return 1;
        } catch (IOException exception) {
            source.sendFailure(Component.literal("Не удалось очистить выбор следующей карты: " + exception.getMessage()));
            return 0;
        }
    }

    private static String emptyToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}

