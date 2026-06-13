package com.makar.tacticaltablet.command;

import com.makar.tacticaltablet.progression.ClassXPManager;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class DebugXPCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ttxp")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("class", StringArgumentType.word())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String clazz = StringArgumentType.getString(ctx, "class");
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");

                                    ClassXPManager.addXP(player, clazz, amount);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Добавлено " + amount + " опыта классу " + clazz),
                                            false
                                    );
                                    return 1;
                                })
                        )
                )
        );
    }
}

