package com.makar.tacticaltablet.command;

import com.makar.tacticaltablet.corpse.CorpseTestManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class CorpseTestCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ttcorpse")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("selfloot")
                        .executes(ctx -> status(ctx.getSource()))
                        .then(Commands.literal("on")
                                .executes(ctx -> setSelfLoot(ctx.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(ctx -> setSelfLoot(ctx.getSource(), false)))
                        .then(Commands.literal("toggle")
                                .executes(ctx -> setSelfLoot(ctx.getSource(), CorpseTestManager.toggleOwnCorpseLoot())))));
    }

    private static int setSelfLoot(CommandSourceStack source, boolean enabled) {
        CorpseTestManager.setOwnCorpseLootEnabled(enabled);
        source.sendSuccess(
                () -> Component.literal("[WAR] Тест лута своего трупа: " + (enabled ? "включён" : "выключен")),
                true
        );
        return enabled ? 1 : 0;
    }

    private static int status(CommandSourceStack source) {
        boolean enabled = CorpseTestManager.canLootOwnCorpses();
        source.sendSuccess(
                () -> Component.literal("[WAR] Тест лута своего трупа: " + (enabled ? "включён" : "выключен")),
                false
        );
        return enabled ? 1 : 0;
    }
}
