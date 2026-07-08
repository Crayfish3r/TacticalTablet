package com.makar.tacticaltablet.command;

import com.makar.tacticaltablet.account.PlayerIdentity;
import com.makar.tacticaltablet.account.PlayerLookup;
import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public class CoinCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ttcoins")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("get")
                        .then(Commands.argument("user", StringArgumentType.word())
                                .executes(ctx -> getCoins(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "user")
                                ))))
                .then(Commands.literal("give")
                        .then(Commands.argument("user", StringArgumentType.word())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> addCoins(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "user"),
                                                IntegerArgumentType.getInteger(ctx, "amount")
                                        )))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("user", StringArgumentType.word())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> addCoins(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "user"),
                                                -IntegerArgumentType.getInteger(ctx, "amount")
                                        )))))
                .then(Commands.literal("set")
                        .then(Commands.argument("user", StringArgumentType.word())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(ctx -> setCoins(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "user"),
                                                IntegerArgumentType.getInteger(ctx, "amount")
                                        )))))
        );
    }

    private static int getCoins(CommandSourceStack source, String user) {
        Optional<PlayerIdentity> resolved = PlayerLookup.resolve(source.getServer(), user);
        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal(PlayerLookup.NOT_FOUND_MESSAGE));
            return 0;
        }

        PlayerIdentity target = resolved.get();
        int coins = PlayerProgressManager.getCoins(source.getServer(), target.uuid(), target.name());
        source.sendSuccess(
                () -> Component.literal("[TTCoins] " + target.name() + ": " + coins + " монет."),
                false
        );
        return coins;
    }

    private static int addCoins(CommandSourceStack source, String user, int amount) {
        Optional<PlayerIdentity> resolved = PlayerLookup.resolve(source.getServer(), user);
        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal(PlayerLookup.NOT_FOUND_MESSAGE));
            return 0;
        }

        PlayerIdentity target = resolved.get();
        if (!PlayerProgressManager.addCoins(source.getServer(), target.uuid(), target.name(), amount)) {
            source.sendFailure(Component.literal("[TTCoins] Не удалось сохранить монеты для " + target.name() + "."));
            return 0;
        }

        ServerPlayer online = PlayerLookup.getOnline(source.getServer(), target);
        if (online != null) {
            ClassXPManager.sync(online);
        }

        int visibleAmount = Math.abs(amount);
        source.sendSuccess(
                () -> Component.literal(buildAddCoinsMessage(target, amount, visibleAmount)),
                true
        );
        return 1;
    }

    private static int setCoins(CommandSourceStack source, String user, int amount) {
        Optional<PlayerIdentity> resolved = PlayerLookup.resolve(source.getServer(), user);
        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal(PlayerLookup.NOT_FOUND_MESSAGE));
            return 0;
        }

        PlayerIdentity target = resolved.get();
        if (!PlayerProgressManager.setCoins(source.getServer(), target.uuid(), target.name(), amount)) {
            source.sendFailure(Component.literal("[TTCoins] Не удалось сохранить монеты для " + target.name() + "."));
            return 0;
        }

        ServerPlayer online = PlayerLookup.getOnline(source.getServer(), target);
        if (online != null) {
            ClassXPManager.sync(online);
        }

        source.sendSuccess(
                () -> Component.literal(target.online()
                        ? "[TTCoins] Игроку " + target.name() + " установлен баланс " + amount + " монет."
                        : "[TTCoins] Offline-игроку " + target.name() + " установлен баланс " + amount
                        + " монет. Применится при следующем входе."),
                true
        );
        return 1;
    }

    private static String buildAddCoinsMessage(PlayerIdentity target, int amount, int visibleAmount) {
        boolean give = amount >= 0;
        if (target.online()) {
            return give
                    ? "[TTCoins] Игроку " + target.name() + " выдано " + visibleAmount + " монет."
                    : "[TTCoins] У игрока " + target.name() + " снято " + visibleAmount + " монет.";
        }

        return give
                ? "[TTCoins] Offline-игроку " + target.name() + " выдано " + visibleAmount
                + " монет. Применится при следующем входе."
                : "[TTCoins] У offline-игрока " + target.name() + " снято " + visibleAmount
                + " монет. Применится при следующем входе.";
    }
}
