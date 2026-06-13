package com.makar.tacticaltablet.command;

import com.makar.tacticaltablet.game.lobby.LobbyManager;
import com.makar.tacticaltablet.progression.ClassCooldownManager;
import com.makar.tacticaltablet.tablet.PlayerTabletState;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

public class ResetCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ttreset")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    resetPlayer(player);
                    ctx.getSource().sendSuccess(() -> Component.literal("Планшет сброшен для себя"), false);
                    return 1;
                })
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                            resetPlayer(target);
                            ctx.getSource().sendSuccess(() -> Component.literal("Планшет сброшен: " + target.getName().getString()), false);
                            return 1;
                        })
                )
                .then(Commands.literal("all")
                        .executes(ctx -> {
                            Collection<ServerPlayer> players = ctx.getSource().getServer().getPlayerList().getPlayers();
                            for (ServerPlayer player : players) {
                                resetPlayer(player);
                            }
                            ClassCooldownManager.resetAll();
                            ctx.getSource().sendSuccess(() -> Component.literal("Планшеты сброшены для всех игроков"), true);
                            return players.size();
                        })
                )
        );
    }

    private static void resetPlayer(ServerPlayer player) {
        ClassCooldownManager.reset(player);
        PlayerTabletState.reset(player);
        LobbyManager.sync(player);
    }
}

