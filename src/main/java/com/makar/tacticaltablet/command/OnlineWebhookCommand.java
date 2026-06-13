package com.makar.tacticaltablet.command;

import com.makar.tacticaltablet.integration.online.OnlineWebhookConfig;
import com.makar.tacticaltablet.integration.online.OnlineWebhookService;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class OnlineWebhookCommand {

    private OnlineWebhookCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("onlinewebhook")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource()))
                )
                .then(Commands.literal("reload")
                        .executes(ctx -> reload(ctx.getSource()))
                )
                .then(Commands.literal("send-now")
                        .executes(ctx -> sendNow(ctx.getSource()))
                )
                .then(Commands.literal("clear-message")
                        .executes(ctx -> clearMessage(ctx.getSource()))
                )
        );
    }

    private static int status(CommandSourceStack source) {
        OnlineWebhookConfig config = OnlineWebhookConfig.get(source.getServer());

        source.sendSuccess(() -> Component.literal("Онлайн-вебхук тактического планшета:"), false);
        source.sendSuccess(() -> Component.literal("- включён: " + config.isEnabled()), false);
        source.sendSuccess(() -> Component.literal("- настроен: " + config.hasWebhook()), false);
        source.sendSuccess(() -> Component.literal("- интервал обновления, сек.: " + config.getUpdateIntervalSeconds()), false);
        source.sendSuccess(() -> Component.literal("- интервал рестарта, мин.: " + config.getRestartIntervalMinutes()), false);
        source.sendSuccess(() -> Component.literal("- id сообщения: " + (config.hasMessageId() ? "сохранён" : "-")), false);

        return config.hasWebhook() ? 1 : 0;
    }

    private static int reload(CommandSourceStack source) {
        OnlineWebhookConfig.reload(source.getServer());
        source.sendSuccess(() -> Component.literal("Конфиг онлайн-вебхука перезагружен."), true);
        return 1;
    }

    private static int sendNow(CommandSourceStack source) {
        boolean queued = OnlineWebhookService.sendUpdateNow(source.getServer());

        if (!queued) {
            source.sendFailure(Component.literal("Онлайн-вебхук выключен, не настроен или уже занят."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Обновление онлайн-вебхука поставлено в очередь."), true);
        return 1;
    }

    private static int clearMessage(CommandSourceStack source) {
        OnlineWebhookConfig.clearMessageId();
        source.sendSuccess(() -> Component.literal("ID сообщения онлайн-вебхука очищен. Следующее обновление создаст новое сообщение Discord."), true);
        return 1;
    }
}

