package com.makar.tacticaltablet.command;

import com.makar.tacticaltablet.integration.discord.DiscordConfig;
import com.makar.tacticaltablet.integration.discord.DiscordLeaderboardService;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class KillsDiscordCommand {

    private KillsDiscordCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("killsdiscord")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> send(ctx.getSource()))
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            DiscordConfig.reload(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Конфиг рейтинга Discord перезагружен."),
                                    false
                            );
                            return 1;
                        })
                )
        );
    }

    private static int send(CommandSourceStack source) {
        DiscordConfig config = DiscordConfig.get(source.getServer());

        if (!config.hasWebhook()) {
            source.sendFailure(Component.literal(
                    "Discord-вебхук не настроен. Укажи webhookUrl в config/tacticaltablet_discord.json."
            ));
            return 0;
        }

        DiscordLeaderboardService.sendOverallLeaderboard(source.getServer());
        source.sendSuccess(
                () -> Component.literal("Отправка рейтинга убийств в Discord поставлена в очередь."),
                true
        );
        return 1;
    }
}

