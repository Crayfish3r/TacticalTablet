package com.makar.tacticaltablet.command;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.MapSetManager;
import com.makar.tacticaltablet.game.ServerRules;
import com.makar.tacticaltablet.game.SetGameMode;
import com.makar.tacticaltablet.game.npc.NpcEncounterManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class GameAdminCommand {
    private GameAdminCommand() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var mode = Commands.literal("mode").executes(ctx -> modeStatus(ctx.getSource()));
        var next = Commands.literal("next");
        for (SetGameMode option : SetGameMode.values()) {
            if (!option.selectable()) continue;
            String name = option.name().toLowerCase(Locale.ROOT);
            mode.then(Commands.literal(name).executes(ctx -> setMode(ctx.getSource(), option, false)));
            next.then(Commands.literal(name).executes(ctx -> setMode(ctx.getSource(), option, true)));
        }
        dispatcher.register(Commands.literal("ttgame")
                .requires(source -> source.hasPermission(2) && ServerRules.enabled(source.getServer()))
                .then(mode.then(next))
                .then(Commands.literal("npc")
                        .executes(ctx -> npcStatus(ctx.getSource()))
                        .then(Commands.literal("on").executes(ctx -> setNpcs(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> setNpcs(ctx.getSource(), false)))));
    }

    private static int modeStatus(CommandSourceStack source) {
        SetGameMode next = MapSetManager.getAdminNextSetMode();
        source.sendSuccess(() -> Component.literal("Текущий сет — " + MapSetManager.getSetMode().displayName()
                + ". Следующий сет — " + (next == null ? "по результату голосования" : next.displayName())), false);
        return 1;
    }

    private static int setMode(CommandSourceStack source, SetGameMode mode, boolean next) {
        var result = MapSetManager.setAdminMode(source.getServer(), mode, next);
        if (result == MapSetManager.ModeChange.FAILED) {
            source.sendFailure(Component.literal("Не удалось сохранить режим сета."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                (result == MapSetManager.ModeChange.CURRENT ? "Режим текущего сета — " : "Режим следующего сета — ")
                        + mode.displayName()), true);
        return 1;
    }

    private static int npcStatus(CommandSourceStack source) {
        Boolean override = MapSetManager.getNpcEncountersOverride();
        source.sendSuccess(() -> Component.literal("NPC TacticalTablet — "
                + (override == null ? "по настройкам карты" : override ? "включены" : "выключены")), false);
        return 1;
    }

    private static int setNpcs(CommandSourceStack source, boolean enabled) {
        var server = source.getServer();
        if (!MapSetManager.setNpcEncountersOverride(server, enabled)) {
            source.sendFailure(Component.literal("Не удалось сохранить настройку NPC."));
            return 0;
        }
        if (enabled) {
            source.sendSuccess(() -> Component.literal(
                    "NPC TacticalTablet включены со следующего матча, если режим и профиль поддерживают спавн."), true);
            return 1;
        }
        NpcEncounterManager.deactivate();
        try {
            NpcEncounterManager.cleanup(server);
        } catch (RuntimeException exception) {
            TacticalTabletMod.LOGGER.error("Failed to remove disabled match NPCs", exception);
            source.sendFailure(Component.literal(
                    "NPC выключены. Удаление части ботов отложено до очистки матча. Подробности в журнале сервера."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("NPC TacticalTablet выключены. Боты текущего матча удалены."), true);
        return 1;
    }
}
