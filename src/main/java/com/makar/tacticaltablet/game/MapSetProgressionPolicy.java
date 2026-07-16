package com.makar.tacticaltablet.game;

public final class MapSetProgressionPolicy {
    public static final int CURRENT_DATA_VERSION = 5;
    static final int LEGACY_FOUR_GAME_DATA_VERSION = 4;
    static final int LEGACY_GAMES_PER_SET = 4;

    private MapSetProgressionPolicy() {
    }

    public static int currentGameNumber(int completedGames, int gamesPerSet) {
        requirePositiveSetLength(gamesPerSet);
        return Math.min(gamesPerSet, Math.max(1, completedGames + 1));
    }

    public static int normalizeCompletedGames(int completedGames, int gamesPerSet) {
        requirePositiveSetLength(gamesPerSet);
        return Math.max(0, Math.min(gamesPerSet, completedGames));
    }

    public static int completedAfterGame(int completedGames, int gamesPerSet) {
        return normalizeCompletedGames(completedGames + 1, gamesPerSet);
    }

    public static boolean isComplete(int completedGames, int gamesPerSet) {
        requirePositiveSetLength(gamesPerSet);
        return completedGames >= gamesPerSet;
    }

    public static Migration migrate(int dataVersion, int completedGames, int gamesPerSet) {
        int normalizedGames = normalizeCompletedGames(completedGames, gamesPerSet);
        if (dataVersion <= LEGACY_FOUR_GAME_DATA_VERSION
                && completedGames >= LEGACY_GAMES_PER_SET) {
            normalizedGames = gamesPerSet;
        }
        return new Migration(CURRENT_DATA_VERSION, normalizedGames);
    }

    private static void requirePositiveSetLength(int gamesPerSet) {
        if (gamesPerSet <= 0) {
            throw new IllegalArgumentException("gamesPerSet must be positive");
        }
    }

    public record Migration(int dataVersion, int completedGames) {
    }
}
