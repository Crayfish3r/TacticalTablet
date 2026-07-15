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
        List<GamePerformance> games
) {
    public SetPlayerResult {
        playerName = playerName == null || playerName.isBlank() ? "unknown" : playerName;
        games = games == null ? List.of() : List.copyOf(games);
        int calculated = games.stream().mapToInt(SetScoringRules::calculateGameScore).sum();
        if (totalScore != calculated) {
            throw new IllegalArgumentException("Set totalScore does not match game performances");
        }
    }

    public static SetPlayerResult fromGames(UUID playerId, String playerName, List<GamePerformance> games) {
        List<GamePerformance> safeGames = games == null ? List.of() : List.copyOf(games);
        return new SetPlayerResult(
                playerId,
                playerName,
                safeGames.stream().mapToInt(SetScoringRules::calculateGameScore).sum(),
                (int) safeGames.stream().filter(game -> game.placement() == 1).count(),
                safeGames.stream().mapToInt(GamePerformance::kills).sum(),
                safeGames.stream().mapToInt(GamePerformance::assists).sum(),
                safeGames.stream().mapToDouble(GamePerformance::effectivePvpDamage).sum(),
                safeGames.stream().mapToInt(GamePerformance::deaths).sum(),
                safeGames
        );
    }
}
