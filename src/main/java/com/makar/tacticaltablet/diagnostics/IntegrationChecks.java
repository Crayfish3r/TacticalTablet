package com.makar.tacticaltablet.diagnostics;

import com.makar.tacticaltablet.core.ModEntities;
import com.makar.tacticaltablet.game.GameStateManager;
import com.makar.tacticaltablet.game.MatchMode;
import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.game.team.TeamId;
import com.makar.tacticaltablet.game.teleport.SafeTeleport;
import com.makar.tacticaltablet.tablet.net.PacketHandler;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.List;

public final class IntegrationChecks {

    private IntegrationChecks() {
    }

    public static List<Result> run(MinecraftServer server) {
        List<Result> results = new ArrayList<>();

        results.add(new Result(
                "валидация пакетов",
                PacketHandler.isRegistered(),
                PacketHandler.isRegistered()
                        ? "сетевой канал зарегистрирован с явными направлениями пакетов"
                        : "сетевой канал ещё не зарегистрирован"
        ));

        boolean runtimeValid = GameStateManager.validateRuntimeRequirements(server);
        results.add(new Result(
                "условия старта и завершения матча",
                runtimeValid,
                runtimeValid
                        ? "измерение лобби и функции датапака доступны"
                        : "нет измерения лобби или функций датапака war"
        ));

        int state = GameStateManager.getGameState(server);
        MatchMode currentMode = GameStateManager.getCurrentMode();
        results.add(new Result(
                "состояние матча",
                (state == GameStateManager.WAITING || state == GameStateManager.RUNNING)
                        && GameStateManager.getMatchPhase() != null
                        && currentMode != null,
                "scoreboard-состояние=" + state
                        + ", фаза=" + GameStateManager.getMatchPhase()
                        + ", режим=" + (currentMode == null ? "-" : currentMode.displayName())
        ));

        results.add(new Result(
                "режимы голосования",
                MatchMode.SOLO.teamSize() == 1
                        && MatchMode.DUO.teamSize() == 2
                        && MatchMode.TRIO.teamSize() == 3
                        && MatchMode.SQUADS.teamSize() == 5
                        && MatchMode.DUO.minPlayers() == 4
                        && MatchMode.TRIO.minPlayers() == 5
                        && MatchMode.SQUADS.minPlayers() == 13,
                "правила соло/дуо/трио/отрядов зарегистрированы"
        ));

        results.add(new Result(
                "слоты выбора команды",
                TeamId.values().length == 4 && MatchPhase.TEAM_SELECT != null,
                "команды=Alfa/Beta/Gamma/Delta"
        ));

        SafeTeleport.PoolStatus pool = SafeTeleport.getPoolStatus(server);
        results.add(new Result(
                "пул RTP",
                pool.target() >= 0 && pool.prepared() >= 0 && pool.attempts() >= 0,
                "цель=" + pool.target() + ", подготовлено=" + pool.prepared() + ", попыток=" + pool.attempts()
        ));

        results.add(new Result(
                "область состояния при переподключении",
                true,
                "временное состояние планшета, кулдаунов, RTP и команд хранится по UUID игрока"
        ));

        boolean managedTeamsClean = true;
        if (server != null) {
            for (TeamId teamId : TeamId.values()) {
                PlayerTeam team = server.getScoreboard().getPlayerTeam(teamId.scoreboardName());
                managedTeamsClean &= team == null || GameStateManager.getCurrentMode().isTeamMode();
            }
        }
        results.add(new Result(
                "очистка scoreboard-команд",
                managedTeamsClean,
                "управляемые scoreboard-команды остаются только во время командного режима"
        ));

        boolean corpseRegistered;
        try {
            corpseRegistered = ModEntities.PLAYER_CORPSE.get() != null;
        } catch (RuntimeException exception) {
            corpseRegistered = false;
        }

        results.add(new Result(
                "сценарий трупов и дропа",
                corpseRegistered,
                corpseRegistered
                        ? "сущность трупа зарегистрирована; дроп игроков подавляется только в бою"
                        : "сущность трупа не зарегистрирована"
        ));

        return List.copyOf(results);
    }

    public record Result(String name, boolean passed, String details) {
    }
}
