package com.makar.tacticaltablet.command;

import com.makar.tacticaltablet.account.PlayerIdentity;
import com.makar.tacticaltablet.account.PlayerLookup;
import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager.ExclusiveClassGrantResult;

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
import java.util.Optional;

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
                .then(Commands.literal("user")
                        .then(Commands.argument("user", StringArgumentType.word())
                                .then(Commands.argument("class", StringArgumentType.string())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(CLASS_KEYS, builder))
                                        .executes(context -> grantUser(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "user"),
                                                StringArgumentType.getString(context, "class")
                                        )))))
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
        int failed = 0;
        for (ServerPlayer target : targets) {
            ExclusiveClassGrantResult result = PlayerProgressManager.grantExclusiveClass(
                    source.getServer(),
                    target.getUUID(),
                    target.getGameProfile().getName(),
                    classKey
            );
            switch (result) {
                case GRANTED -> {
                    ClassXPManager.sync(target);
                    target.sendSystemMessage(Component.literal(
                            "[WAR] Тебе выдан эксклюзивный класс " + displayName(classKey) + "."
                    ));
                    granted++;
                }
                case ALREADY_OWNED -> alreadyOwned++;
                case INVALID_CLASS, SAVE_FAILED -> {
                    failed++;
                    source.sendFailure(Component.literal(
                            "Не удалось сохранить класс для игрока " + target.getGameProfile().getName() + ". Изменение не применено."
                    ));
                }
            }
        }

        int grantedCount = granted;
        int alreadyOwnedCount = alreadyOwned;
        if (granted == 0 && failed > 0) return 0;
        source.sendSuccess(
                () -> Component.literal(
                        "Класс " + displayName(classKey) + ": выдан " + grantedCount
                                + ", уже был выдан " + alreadyOwnedCount + "."
                ),
                true
        );
        return granted;
    }

    private static int grantUser(CommandSourceStack source, String userInput, String requestedClass) {
        Optional<PlayerIdentity> resolved = PlayerLookup.resolve(source.getServer(), userInput);
        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal(PlayerLookup.NOT_FOUND_MESSAGE));
            return 0;
        }

        String classKey = normalizeClassKey(requestedClass);
        if (classKey == null) {
            sendInvalidClass(source);
            return 0;
        }

        PlayerIdentity identity = resolved.get();
        ExclusiveClassGrantResult result = PlayerProgressManager.grantExclusiveClass(
                source.getServer(), identity.uuid(), identity.name(), classKey
        );
        return switch (result) {
            case GRANTED -> grantResolvedUser(source, identity, classKey);
            case ALREADY_OWNED -> {
                source.sendSuccess(
                        () -> Component.literal("У игрока " + identity.name() + " уже есть класс " + displayName(classKey) + "."),
                        false
                );
                yield 1;
            }
            case INVALID_CLASS -> {
                sendInvalidClass(source);
                yield 0;
            }
            case SAVE_FAILED -> {
                source.sendFailure(Component.literal(
                        "Не удалось сохранить класс для игрока " + identity.name() + ". Изменение не применено."
                ));
                yield 0;
            }
        };
    }

    private static int grantResolvedUser(CommandSourceStack source, PlayerIdentity identity, String classKey) {
        ServerPlayer online = PlayerLookup.getOnline(source.getServer(), identity);
        if (online != null) {
            ClassXPManager.sync(online);
            online.sendSystemMessage(Component.literal(
                    "[WAR] Тебе выдан эксклюзивный класс " + displayName(classKey) + "."
            ));
        }

        source.sendSuccess(
                () -> Component.literal(online != null
                        ? "Класс " + displayName(classKey) + " выдан игроку " + identity.name() + "."
                        : "Класс " + displayName(classKey) + " выдан offline-игроку " + identity.name()
                        + ". Изменение применится при следующем входе."),
                true
        );
        return 1;
    }

    private static void sendInvalidClass(CommandSourceStack source) {
        source.sendFailure(Component.literal(
                "Неизвестный эксклюзивный класс. Доступно: killer, miniboss, shahed, krot, medic, microwave, railgunner."
        ));
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
