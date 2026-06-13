package com.makar.tacticaltablet.stats;

import java.util.Locale;
import java.util.Objects;

public final class PlayerStats {

    private final String name;
    private final int kills;
    private final int deaths;
    private final int wins;
    private final int matchesPlayed;
    private final int coins;

    public PlayerStats(String name, int kills, int deaths, int wins, int matchesPlayed, int coins) {
        this.name = sanitizeName(name);
        this.kills = Math.max(0, kills);
        this.deaths = Math.max(0, deaths);
        this.wins = Math.max(0, wins);
        this.matchesPlayed = Math.max(0, matchesPlayed);
        this.coins = Math.max(0, coins);
    }

    public String getName() {
        return name;
    }

    public int getKills() {
        return kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public int getWins() {
        return wins;
    }

    public int getMatchesPlayed() {
        return matchesPlayed;
    }

    public int getCoins() {
        return coins;
    }

    public double getKd() {
        if (deaths <= 0) {
            return kills;
        }

        return (double) kills / deaths;
    }

    public String getFormattedKd() {
        return String.format(Locale.US, "%.2f", getKd());
    }

    private static String sanitizeName(String rawName) {
        String value = Objects.toString(rawName, "unknown")
                .replace('\n', '_')
                .replace('\r', '_')
                .replace('`', '\'')
                .trim();

        if (value.isBlank()) {
            return "unknown";
        }

        return value.length() > 17 ? value.substring(0, 17) : value;
    }
}

