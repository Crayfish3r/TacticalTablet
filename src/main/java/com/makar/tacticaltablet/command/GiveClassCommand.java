package com.makar.tacticaltablet.command;

import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class GiveClassCommand {

    private static final List<String> CLASS_KEYS = List.of("killer", "miniboss", "shahed", "krot", "medic", "microwave", "railgunner");

    private GiveClassCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ttgiveclass")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("class", StringArgumentType.string())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(CLASS_KEYS, builder))
                        .executes(context -> grant(
                                context.getSource(),
                                List.of(context.getSource().getPlayerOrException()),
                                StringArgumentType.getString(context, "class")
                        )))
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("class", StringArgumentType.string())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(CLASS_KEYS, builder))
                                .executes(context -> grant(
                                        context.getSource(),
                                        EntityArgument.getPlayers(context, "targets"),
                                        StringArgumentType.getString(context, "class")
                                ))))
        );
    }

    private static int grant(
            CommandSourceStack source,
            Collection<ServerPlayer> targets,
            String requestedClass
    ) {
        String classKey = normalizeClassKey(requestedClass);
        if (classKey == null) {
            source.sendFailure(Component.literal(
                    "Неизвестный эксклюзивный класс. Доступно: killer, miniboss, shahed, krot, medic, microwave, railgunner."
            ));
            return 0;
        }

        int granted = 0;
        int alreadyOwned = 0;
        for (ServerPlayer target : targets) {
            if (PlayerProgressManager.grantExclusiveClass(target, classKey)) {
                PlayerProgressManager.savePlayer(target);
                ClassXPManager.sync(target);
                target.sendSystemMessage(Component.literal(
                        "[WAR] Тебе выдан эксклюзивный класс " + displayName(classKey) + "."
                ));
                granted++;
            } else if (PlayerProgressManager.isExclusiveClassGranted(target, classKey)) {
                alreadyOwned++;
            }
        }

        int grantedCount = granted;
        int alreadyOwnedCount = alreadyOwned;
        source.sendSuccess(
                () -> Component.literal(
                        "Класс " + displayName(classKey) + ": выдан " + grantedCount
                                + ", уже был выдан " + alreadyOwnedCount + "."
                ),
                true
        );
        return granted;
    }

    private static String normalizeClassKey(String value) {
        if (value == null) return null;

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "killer", "киллер" -> "killer";
            case "miniboss", "mini-boss", "mini_boss", "мини-босс", "минибосс", "мини_босс" -> "miniboss";
            case "shahed", "shahedop", "shahed_op", "shahed-op", "шахед", "шахедоп", "шахед_оп", "шахед-оп", "шахед оп." -> "shahed";
            case "krot", "крот" -> "krot";
            case "medic", "медик" -> "medic";
            case "microwave", "micro-wave", "micro_wave", "микровэйв", "микровейв" -> "microwave";
            case "railgunner", "rail-gunner", "rail_gunner", "рэйл-ганнер", "рэйлганнер", "рэйл_ганнер", "рейл-ганнер", "рейлганнер" -> "railgunner";
            default -> null;
        };
    }

    private static String displayName(String classKey) {
        return switch (classKey) {
            case "miniboss" -> "Мини-Босс";
            case "shahed" -> "Шахед оп.";
            case "krot" -> "Крот";
            case "medic" -> "Медик";
            case "microwave" -> "Микровэйв";
            case "railgunner" -> "Рэйл-ганнер";
            default -> "Киллер";
        };
    }
}
