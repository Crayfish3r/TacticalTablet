package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.stats.PlayerStats;

import java.util.Locale;
import java.util.Objects;

/** Immutable, runtime-object-free spectator HUD payload assembled on the server thread. */
public record SpectatorHudSnapshot(
        String playerName,
        String className,
        int kills,
        int deaths,
        int wins,
        int matchesPlayed
) {
    public static final int MAX_PLAYER_NAME_LENGTH = 32;
    public static final int MAX_CLASS_NAME_LENGTH = 64;

    public SpectatorHudSnapshot {
        playerName = sanitize(playerName, MAX_PLAYER_NAME_LENGTH);
        className = sanitize(className, MAX_CLASS_NAME_LENGTH);
        kills = Math.max(0, kills);
        deaths = Math.max(0, deaths);
        wins = Math.max(0, wins);
        matchesPlayed = Math.max(0, matchesPlayed);
    }

    public static SpectatorHudSnapshot from(PlayerStats stats, String className) {
        Objects.requireNonNull(stats, "stats");
        return new SpectatorHudSnapshot(
                stats.getName(), className, stats.getKills(), stats.getDeaths(),
                stats.getWins(), stats.getMatchesPlayed());
    }

    public String formattedKd() {
        double kd = deaths == 0 ? kills : kills / (double) deaths;
        return String.format(Locale.ROOT, "%.2f", kd);
    }

    private static String sanitize(String value, int maxLength) {
        String sanitized = Objects.toString(value, "")
                .replace('\n', '_')
                .replace('\r', '_')
                .trim();
        return sanitized.length() > maxLength ? sanitized.substring(0, maxLength) : sanitized;
    }
}
