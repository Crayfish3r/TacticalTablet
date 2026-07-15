package com.makar.tacticaltablet.command;

import com.makar.tacticaltablet.admin.TestModeManager;
import com.makar.tacticaltablet.game.contract.ContractManager;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MapSetManager;
import com.makar.tacticaltablet.game.MatchMode;
import com.makar.tacticaltablet.game.clanwar.ClanWarManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

public class TestModeCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tttest")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> sendStatus(ctx.getSource()))
                .then(Commands.literal("solo")
                        .executes(ctx -> sendStatus(ctx.getSource()))
                        .then(Commands.literal("on")
                                .executes(ctx -> setSoloMode(ctx.getSource(), true))
                        )
                        .then(Commands.literal("off")
                                .executes(ctx -> setSoloMode(ctx.getSource(), false))
                        )
                        .then(Commands.literal("toggle")
                                .executes(ctx -> setSoloMode(
                                        ctx.getSource(),
                                        !TestModeManager.isSoloStartEnabled()
                                ))
                        )
                )
                .then(Commands.literal("start")
                        .executes(ctx -> {
                            TestModeManager.setSoloStartEnabled(true);
                            GameStateManager.startGame(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("[WAR] Соло-тест включён, матч принудительно запущен."),
                                    true
                            );
                            return 1;
                        })
                )
                .then(Commands.literal("stop")
                        .executes(ctx -> stopMatch(ctx.getSource()))
                )
                .then(Commands.literal("clanwar")
                        .executes(ctx -> sendClanWarStatus(ctx.getSource()))
                        .then(Commands.literal("start")
                                .executes(ctx -> startClanWarDebug(ctx.getSource(), true))
                        )
                        .then(Commands.literal("wait")
                                .executes(ctx -> startClanWarDebug(ctx.getSource(), false))
                        )
                        .then(Commands.literal("solo")
                                .then(Commands.literal("on")
                                        .executes(ctx -> setClanWarSoloDebug(ctx.getSource(), true))
                                )
                                .then(Commands.literal("off")
                                        .executes(ctx -> setClanWarSoloDebug(ctx.getSource(), false))
                                )
                        )
                        .then(Commands.literal("status")
                                .executes(ctx -> sendClanWarStatus(ctx.getSource()))
                        )
                )
                .then(Commands.literal("lowplayers")
                        .executes(ctx -> sendStatus(ctx.getSource()))
                        .then(Commands.literal("on")
                                .executes(ctx -> setLowPlayerTeamTests(ctx.getSource(), true))
                        )
                        .then(Commands.literal("off")
                                .executes(ctx -> setLowPlayerTeamTests(ctx.getSource(), false))
                        )
                )
                .then(Commands.literal("vote")
                        .then(Commands.literal("start")
                                .executes(ctx -> startDebugVote(ctx.getSource()))
                        )
                        .then(Commands.literal("stop")
                                .executes(ctx -> stopDebugVote(ctx.getSource()))
                        )
                )
                .then(Commands.literal("mapvote")
                        .executes(ctx -> startDebugMapVote(ctx.getSource()))
                )
                .then(Commands.literal("teamselect")
                        .then(Commands.literal("duo")
                                .executes(ctx -> startDebugTeamSelect(ctx.getSource(), MatchMode.DUO))
                        )
                        .then(Commands.literal("trio")
                                .executes(ctx -> startDebugTeamSelect(ctx.getSource(), MatchMode.TRIO))
                        )
                        .then(Commands.literal("squads")
                                .executes(ctx -> startDebugTeamSelect(ctx.getSource(), MatchMode.SQUADS))
                        )
                )
                .then(Commands.literal("contract")
                        .executes(ctx -> sendContractStatus(ctx.getSource()))
                        .then(Commands.literal("solo")
                                .then(Commands.literal("on")
                                        .executes(ctx -> setContractSoloDebug(ctx.getSource(), true))
                                )
                                .then(Commands.literal("off")
                                        .executes(ctx -> setContractSoloDebug(ctx.getSource(), false))
                                )
                        )
                        .then(Commands.literal("start")
                                .executes(ctx -> startContractSelection(ctx.getSource()))
                        )
                        .then(Commands.literal("tracker")
                                .executes(ctx -> openContractTracker(ctx.getSource()))
                        )
                        .then(Commands.literal("self")
                                .executes(ctx -> createSelfContract(ctx.getSource()))
                        )
                        .then(Commands.literal("stand")
                                .executes(ctx -> createArmorStandContract(ctx.getSource()))
                        )
                        .then(Commands.literal("cooldown")
                                .then(Commands.literal("reset")
                                        .then(Commands.literal("all")
                                                .executes(ctx -> resetContractCooldowns(ctx.getSource()))
                                        )
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .executes(ctx -> resetContractCooldown(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayers(ctx, "player")
                                                ))
                                        )
                                )
                        )
                )
        );
    }

    private static int setSoloMode(CommandSourceStack source, boolean enabled) {
        TestModeManager.setSoloStartEnabled(enabled);
        source.sendSuccess(
                () -> Component.literal("[WAR] " + TestModeManager.getStatusText()),
                true
        );
        return enabled ? 1 : 0;
    }

    private static int setLowPlayerTeamTests(CommandSourceStack source, boolean enabled) {
        TestModeManager.setLowPlayerTeamTestsEnabled(enabled);
        source.sendSuccess(
                () -> Component.literal("[WAR] " + TestModeManager.getStatusText()),
                true
        );
        return enabled ? 1 : 0;
    }

    private static int startDebugVote(CommandSourceStack source) {
        TestModeManager.setLowPlayerTeamTestsEnabled(true);
        boolean started = GameStateManager.forceStartVoting(source.getServer());
        source.sendSuccess(
                () -> Component.literal(started
                        ? "[WAR] Отладочное голосование началось. Командные тесты с малым числом игроков включены."
                        : "[WAR] Нельзя запустить отладочное голосование во время матча."),
                true
        );
        return started ? 1 : 0;
    }

    private static int startDebugMapVote(CommandSourceStack source) {
        TestModeManager.setSoloStartEnabled(true);
        boolean started = GameStateManager.forceStartMapVoting(source.getServer());
        if (started) {
            source.sendSuccess(
                    () -> Component.literal("[WAR] Запущено полное отладочное голосование за карту. "
                            + "После 30 секунд сервер выберет карту, подготовит ротацию и остановится."),
                    true
            );
            return 1;
        }

        source.sendFailure(Component.literal("[WAR] Нельзя начать голосование за карту во время активного матча."));
        return 0;
    }

    private static int stopDebugVote(CommandSourceStack source) {
        GameStateManager.forceStopMatch(source.getServer());
        TestModeManager.setLowPlayerTeamTestsEnabled(false);
        source.sendSuccess(
                () -> Component.literal("[WAR] Отладочное голосование остановлено. Командные тесты с малым числом игроков выключены."),
                true
        );
        return 1;
    }

    private static int startDebugTeamSelect(CommandSourceStack source, MatchMode mode) {
        TestModeManager.setLowPlayerTeamTestsEnabled(true);
        boolean started = GameStateManager.forceStartTeamSelect(source.getServer(), mode);
        source.sendSuccess(
                () -> Component.literal(started
                        ? "[WAR] Отладочный выбор команды начался: " + mode.displayName()
                        + ". Командные тесты с малым числом игроков включены."
                        : "[WAR] Нельзя запустить отладочный выбор команды во время матча."),
                true
        );
        return started ? 1 : 0;
    }

    private static int stopMatch(CommandSourceStack source) {
        boolean stopped = GameStateManager.forceStopMatch(source.getServer());
        TestModeManager.setLowPlayerTeamTestsEnabled(false);
        source.sendSuccess(
                () -> Component.literal(stopped
                        ? "[WAR] Матч принудительно остановлен. Тестовый режим малого числа игроков выключен."
                        : "[WAR] Состояние матча сброшено. Тестовый режим малого числа игроков выключен."),
                true
        );
        return stopped ? 1 : 0;
    }

    private static int startClanWarDebug(CommandSourceStack source, boolean skipWait) {
        TestModeManager.setSoloStartEnabled(true);
        TestModeManager.setLowPlayerTeamTestsEnabled(true);
        ClanWarManager.setSoloDebugEnabled(true);
        MapSetManager.setDebugClanWarSet(source.getServer(), true);

        boolean started = GameStateManager.forceStartClanWar(source.getServer(), skipWait);
        source.sendSuccess(
                () -> Component.literal(started
                        ? "[WAR] Clan-war debug started. solo=true, lowplayers=true, skipWait=" + skipWait + "."
                        : "[WAR] Clan-war debug cannot start during an active match."),
                true
        );
        return started ? 1 : 0;
    }

    private static int setClanWarSoloDebug(CommandSourceStack source, boolean enabled) {
        ClanWarManager.setSoloDebugEnabled(enabled);
        source.sendSuccess(
                () -> Component.literal("[WAR] Clan-war solo debug: " + (enabled ? "on" : "off") + "."),
                true
        );
        return enabled ? 1 : 0;
    }

    private static int sendClanWarStatus(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal("[WAR] Clan-war currentSet=" + MapSetManager.isClanWarSet()
                        + ", soloDebug=" + ClanWarManager.isSoloDebugEnabled()
                        + ", soloStart=" + TestModeManager.isSoloStartEnabled()
                        + ", lowplayers=" + TestModeManager.isLowPlayerTeamTestsEnabled() + "."),
                false
        );
        return 1;
    }

    private static int setContractSoloDebug(CommandSourceStack source, boolean enabled) {
        ContractManager.setSoloDebugEnabled(enabled);
        source.sendSuccess(
                () -> Component.literal("[WAR] Тест контрактов в соло: " + (enabled ? "включён" : "выключен") + "."),
                true
        );
        return enabled ? 1 : 0;
    }

    private static int startContractSelection(CommandSourceStack source) {
        ContractManager.setSoloDebugEnabled(true);
        ContractManager.forceStartSelection(source.getServer());
        source.sendSuccess(
                () -> Component.literal("[WAR] Выбор контрактов принудительно открыт. Соло-тест контрактов включён."),
                true
        );
        return 1;
    }

    private static int openContractTracker(CommandSourceStack source) {
        if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ContractManager.ensureTracker(player);
            ContractManager.onTrackerUsed(player);
            return 1;
        }

        source.sendFailure(Component.literal("[WAR] Команду tracker нужно выполнять игроком."));
        return 0;
    }

    private static int createSelfContract(CommandSourceStack source) {
        if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ContractManager.setSoloDebugEnabled(true);
            boolean created = ContractManager.createDebugSelfContract(player);
            source.sendSuccess(
                    () -> Component.literal(created
                            ? "[WAR] Тестовый контракт на себя создан. Это только для проверки GUI."
                            : "[WAR] Не удалось создать тестовый контракт."),
                    true
            );
            return created ? 1 : 0;
        }

        source.sendFailure(Component.literal("[WAR] Команду self нужно выполнять игроком."));
        return 0;
    }

    private static int createArmorStandContract(CommandSourceStack source) {
        if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ContractManager.setSoloDebugEnabled(true);
            boolean created = ContractManager.createDebugArmorStandContract(player);
            source.sendSuccess(
                    () -> Component.literal(created
                            ? "[WAR] Тестовый стенд контракта создан в случайной точке зоны."
                            : "[WAR] Не удалось создать тестовый стенд."),
                    true
            );
            return created ? 1 : 0;
        }

        source.sendFailure(Component.literal("[WAR] Команду stand нужно выполнять игроком."));
        return 0;
    }

    private static int resetContractCooldowns(CommandSourceStack source) {
        ContractManager.resetPickCooldowns(source.getServer());
        source.sendSuccess(
                () -> Component.literal("[WAR] Перезарядка выбора контракта сброшена для всех. Сейчас кд контрактов отключён: 1 матч = 1 контракт."),
                true
        );
        return 1;
    }

    private static int resetContractCooldown(CommandSourceStack source, Collection<ServerPlayer> players) {
        int count = 0;
        for (ServerPlayer player : players) {
            ContractManager.resetPickCooldown(player);
            count++;
        }

        int result = count;
        source.sendSuccess(
                () -> Component.literal("[WAR] Перезарядка выбора контракта сброшена для игроков: " + result + ". Сейчас кд контрактов отключён."),
                true
        );
        return result;
    }

    private static int sendContractStatus(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal("[WAR] Тест контрактов в соло: "
                        + (ContractManager.isSoloDebugEnabled() ? "включён" : "выключен") + "."),
                false
        );
        return ContractManager.isSoloDebugEnabled() ? 1 : 0;
    }

    private static int sendStatus(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal("[WAR] " + TestModeManager.getStatusText()),
                false
        );
        return TestModeManager.isSoloStartEnabled() ? 1 : 0;
    }
}
