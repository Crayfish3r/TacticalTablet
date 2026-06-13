package com.makar.tacticaltablet.command;

import com.makar.tacticaltablet.game.respawn.RespawnControlManager;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class RespawnControlCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ttrespawns")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("disable")
                        .executes(context -> disable(context.getSource())))
                .then(Commands.literal("enable")
                        .executes(context -> enable(context.getSource())))
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource()))));
    }

    private static int disable(CommandSourceStack source) {
        RespawnControlManager.disableRespawns(source.getServer());
        source.sendSuccess(() -> Component.literal("[WAR] Возрождения отключены."), true);
        return 1;
    }

    private static int enable(CommandSourceStack source) {
        RespawnControlManager.enableRespawns(source.getServer());
        source.sendSuccess(() -> Component.literal("[WAR] Возрождения включены."), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal("[WAR] Возрождения отключены: " + RespawnControlManager.areRespawnsDisabled()),
                false
        );
        return 1;
    }
}

