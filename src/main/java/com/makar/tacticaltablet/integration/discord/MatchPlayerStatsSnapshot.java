package com.makar.tacticaltablet.integration.discord;

import java.util.UUID;

public record MatchPlayerStatsSnapshot(
        UUID playerId,
        String playerName,
        int kills,
        int assists,
        int deaths,
        double actualHealthDamage,
        int actualCoinBalance,
        int wins,
        int teamKills
) {
    public MatchPlayerStatsSnapshot {
        playerName = playerName == null || playerName.isBlank() ? "unknown" : playerName;
        kills = Math.max(0, kills);
        assists = Math.max(0, assists);
        deaths = Math.max(0, deaths);
        actualHealthDamage = Math.max(0.0D, actualHealthDamage);
        actualCoinBalance = Math.max(0, actualCoinBalance);
        wins = Math.max(0, wins);
        teamKills = Math.max(0, teamKills);
    }
}
