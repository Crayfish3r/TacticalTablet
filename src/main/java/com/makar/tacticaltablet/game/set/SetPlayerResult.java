package com.makar.tacticaltablet.game.set;

import java.util.List;
import java.util.UUID;

public record SetPlayerResult(
        UUID playerId,
        String playerName,
        int totalScore,
        int wins,
        int kills,
        int assists,
        double effectivePvpDamage,
        int deaths,
        List<GamePerformance> games,
        int npcKills
) {
    public SetPlayerResult {
        playerName = playerName == null || playerName.isBlank() ? "unknown" : playerName;
        games = games == null ? List.of() : List.copyOf(games);
        npcKills = Math.max(0, npcKills);
        int calculated = SetScoringRules.calculateTotal(games);
        if (totalScore != calculated) {
            throw new IllegalArgumentException("Set totalScore does not match game performances");
        }
    }

    public SetPlayerResult(UUID playerId, String playerName, int totalScore, int wins, int kills, int assists,
                           double effectivePvpDamage, int deaths, List<GamePerformance> games) {
        this(playerId, playerName, totalScore, wins, kills, assists, effectivePvpDamage, deaths, games,
                games == null ? 0 : games.stream().mapToInt(GamePerformance::npcKills).sum());
    }

    public static SetPlayerResult fromGames(UUID playerId, String playerName, List<GamePerformance> games) {
        List<GamePerformance> safeGames = games == null ? List.of() : List.copyOf(games);
        return new SetPlayerResult(
                playerId,
                playerName,
                SetScoringRules.calculateTotal(safeGames),
                (int) safeGames.stream().filter(game -> game.arena() ? game.arenaWinner() : game.placement() == 1).count(),
                safeGames.stream().mapToInt(GamePerformance::kills).sum(),
                safeGames.stream().mapToInt(GamePerformance::assists).sum(),
                safeGames.stream().mapToDouble(GamePerformance::effectivePvpDamage).sum(),
                safeGames.stream().mapToInt(GamePerformance::deaths).sum(),
                safeGames
        );
    }
}
