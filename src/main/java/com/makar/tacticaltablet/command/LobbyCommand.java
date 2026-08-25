package com.makar.tacticaltablet.command;

import com.makar.tacticaltablet.game.lobby.LobbyBootstrapManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class LobbyCommand {
    private LobbyCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ttlobby")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("repair")
                        .executes(context -> {
                            int repaired = LobbyBootstrapManager.repairMissingFragileBlocks(
                                    context.getSource().getServer());
                            if (repaired < 0) {
                                context.getSource().sendFailure(Component.literal("Lobby repair failed; check server log."));
                                return 0;
                            }
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Restored missing lobby moss carpets: " + repaired),
                                    true
                            );
                            return 1;
                        })));
    }
}
