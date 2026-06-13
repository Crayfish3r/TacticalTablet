package com.makar.tacticaltablet.command;

import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

public class CoinCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ttcoins")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("get")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                    int coins = PlayerProgressManager.getCoins(target);

                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal(target.getGameProfile().getName()
                                                    + ": " + coins + " монет."),
                                            false
                                    );
                                    return coins;
                                })
                        )
                )
                .then(Commands.literal("give")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");

                                            for (ServerPlayer target : targets) {
                                                PlayerProgressManager.addCoins(target, amount);
                                                PlayerProgressManager.savePlayer(target);
                                                ClassXPManager.sync(target);
                                            }

                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Добавлено " + amount + " монет игрокам: "
                                                            + targets.size() + "."),
                                                    true
                                            );
                                            return targets.size();
                                        })
                                )
                        )
                )
                .then(Commands.literal("remove")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");

                                            for (ServerPlayer target : targets) {
                                                PlayerProgressManager.addCoins(target, -amount);
                                                PlayerProgressManager.savePlayer(target);
                                                ClassXPManager.sync(target);
                                            }

                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Снято " + amount + " монет у игроков: "
                                                            + targets.size() + "."),
                                                    true
                                            );
                                            return targets.size();
                                        })
                                )
                        )
                )
                .then(Commands.literal("set")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");

                                            for (ServerPlayer target : targets) {
                                                PlayerProgressManager.setCoins(target, amount);
                                                PlayerProgressManager.savePlayer(target);
                                                ClassXPManager.sync(target);
                                            }

                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Установлено " + amount + " монет для игроков: "
                                                            + targets.size() + "."),
                                                    true
                                            );
                                            return targets.size();
                                        })
                                )
                        )
                )
        );
    }
}

