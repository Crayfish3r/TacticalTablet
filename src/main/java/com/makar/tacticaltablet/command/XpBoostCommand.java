package com.makar.tacticaltablet.command;

import com.makar.tacticaltablet.account.PlayerIdentity;
import com.makar.tacticaltablet.account.PlayerLookup;
import com.makar.tacticaltablet.progression.ClassXPManager;
import com.makar.tacticaltablet.progression.PlayerProgressManager;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public final class XpBoostCommand {

    private XpBoostCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ttxpboost")
                .requires(source -> source.hasPermission(2))
                .executes(context -> setSelfBoost(context.getSource(), true))
                .then(Commands.argument("user", StringArgumentType.word())
                        .executes(context -> setBoost(
                                context.getSource(),
                                StringArgumentType.getString(context, "user"),
                                true
                        ))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> setBoost(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "user"),
                                        BoolArgumentType.getBool(context, "enabled")
                                ))))
        );
    }

    private static int setSelfBoost(CommandSourceStack source, boolean enabled) {
        try {
            return setBoost(source, source.getPlayerOrException().getGameProfile().getName(), enabled);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendFailure(Component.literal(PlayerLookup.NOT_FOUND_MESSAGE));
            return 0;
        }
    }

    private static int setBoost(CommandSourceStack source, String user, boolean enabled) {
        Optional<PlayerIdentity> resolved = PlayerLookup.resolve(source.getServer(), user);
        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal(PlayerLookup.NOT_FOUND_MESSAGE));
            return 0;
        }

        PlayerIdentity target = resolved.get();
        boolean changed = PlayerProgressManager.isXpBoostEnabled(source.getServer(), target.uuid()) != enabled;
        PlayerProgressManager.setXpBoostEnabled(source.getServer(), target.uuid(), target.name(), enabled);

        ServerPlayer online = PlayerLookup.getOnline(source.getServer(), target);
        if (online != null) {
            ClassXPManager.sync(online);
            if (changed && source.getEntity() != online) {
                online.sendSystemMessage(Component.literal(
                        "[TTXPBoost] Администратор " + (enabled ? "включил" : "выключил") + " вам двойной опыт."
                ));
            }
        }

        source.sendSuccess(
                () -> Component.literal(target.online()
                        ? "[TTXPBoost] Буст " + (enabled ? "выдан" : "отключен") + " игроку " + target.name() + "."
                        : "[TTXPBoost] Буст " + (enabled ? "выдан" : "отключен") + " offline-игроку " + target.name()
                        + ". Применится при следующем входе."),
                true
        );
        return changed ? 1 : 0;
    }
}
