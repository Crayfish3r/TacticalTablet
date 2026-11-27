package com.makar.tacticaltablet.game.set;

import java.util.UUID;

public record GamePerformance(
        int gameNumber,
        UUID playerId,
        String playerName,
        int placement,
        int kills,
        int assists,
        double effectivePvpDamage,
        int deaths,
        String teamId,
        boolean arena,
        int arenaScore,
        int npcKills,
        boolean arenaWinner
) {
    public GamePerformance {
        playerName = playerName == null || playerName.isBlank() ? "unknown" : playerName;
        gameNumber = Math.max(0, gameNumber);
        kills = Math.max(0, kills);
        assists = Math.max(0, assists);
        effectivePvpDamage = Double.isFinite(effectivePvpDamage) ? Math.max(0.0D, effectivePvpDamage) : 0.0D;
        deaths = Math.max(0, deaths);
        teamId = teamId == null ? "" : teamId;
        arenaScore = Math.max(0, arenaScore);
        npcKills = Math.max(0, npcKills);
    }

    public GamePerformance(int gameNumber, UUID playerId, String playerName, int placement,
                           int kills, int assists, double effectivePvpDamage, int deaths, String teamId) {
        this(gameNumber, playerId, playerName, placement, kills, assists, effectivePvpDamage, deaths, teamId,
                false, 0, 0, false);
    }

    public static GamePerformance missed(int gameNumber, UUID playerId, String playerName) {
        return new GamePerformance(gameNumber, playerId, playerName, 0, 0, 0, 0.0D, 0, "");
    }
}
