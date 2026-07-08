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

public final class SadTromboneCommand {

    private SadTromboneCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ttsadthrombone")
                .requires(source -> source.hasPermission(2))
                .executes(context -> setSelfPrivilege(context.getSource(), true))
                .then(Commands.argument("user", StringArgumentType.word())
                        .executes(context -> setPrivilege(
                                context.getSource(),
                                StringArgumentType.getString(context, "user"),
                                true
                        ))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> setPrivilege(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "user"),
                                        BoolArgumentType.getBool(context, "enabled")
                                ))))
        );
    }

    private static int setSelfPrivilege(CommandSourceStack source, boolean enabled) {
        try {
            return setPrivilege(source, source.getPlayerOrException().getGameProfile().getName(), enabled);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendFailure(Component.literal(PlayerLookup.NOT_FOUND_MESSAGE));
            return 0;
        }
    }

    private static int setPrivilege(CommandSourceStack source, String user, boolean enabled) {
        Optional<PlayerIdentity> resolved = PlayerLookup.resolve(source.getServer(), user);
        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal(PlayerLookup.NOT_FOUND_MESSAGE));
            return 0;
        }

        PlayerIdentity target = resolved.get();
        boolean changed = PlayerProgressManager.isSadTromboneKillsEnabled(source.getServer(), target.uuid()) != enabled;
        PlayerProgressManager.setSadTromboneKillsEnabled(source.getServer(), target.uuid(), target.name(), enabled);

        ServerPlayer online = PlayerLookup.getOnline(source.getServer(), target);
        if (online != null) {
            ClassXPManager.sync(online);
            if (changed && source.getEntity() != online) {
                online.sendSystemMessage(Component.literal(
                        "[SadTrombone] Администратор " + (enabled ? "включил" : "выключил")
                                + " вам sad trombone для убитых вами игроков."
                ));
            }
        }

        source.sendSuccess(
                () -> Component.literal(target.online()
                        ? "[SadTrombone] Игроку " + target.name() + " установлено: " + enabled + "."
                        : "[SadTrombone] Offline-игроку " + target.name() + " установлено: " + enabled
                        + ". Применится при следующем входе."),
                true
        );
        return changed ? 1 : 0;
    }
}
