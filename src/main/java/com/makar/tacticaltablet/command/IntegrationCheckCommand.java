package com.makar.tacticaltablet.command;

import com.makar.tacticaltablet.diagnostics.IntegrationChecks;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class IntegrationCheckCommand {

    private IntegrationCheckCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("ttcheck")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> run(context.getSource()))
        );
    }

    private static int run(CommandSourceStack source) {
        int failed = 0;
        source.sendSuccess(() -> Component.literal("[TT] Проверки интеграций:"), false);

        for (IntegrationChecks.Result result : IntegrationChecks.run(source.getServer())) {
            if (!result.passed()) {
                failed++;
            }

            String status = result.passed() ? "ОК" : "ОШИБКА";
            source.sendSuccess(
                    () -> Component.literal("[TT] [" + status + "] " + result.name() + " - " + result.details()),
                    false
            );
        }

        int finalFailed = failed;
        source.sendSuccess(
                () -> Component.literal("[TT] Проверки завершены. Ошибок: " + finalFailed),
                false
        );
        return failed == 0 ? 1 : 0;
    }
}
