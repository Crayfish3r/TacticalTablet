package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapSetProgressionPolicyTest {

    @Test
    void newEmptySetStartsAtGameOneAndCompletesOnlyAfterGameFive() {
        int completedGames = 0;
        assertEquals(1, MapSetProgressionPolicy.currentGameNumber(completedGames, 5));
        assertFalse(MapSetProgressionPolicy.isComplete(completedGames, 5));

        for (int game = 1; game <= 5; game++) {
            assertEquals(game, MapSetProgressionPolicy.currentGameNumber(completedGames, 5));
            completedGames = MapSetProgressionPolicy.completedAfterGame(completedGames, 5);
            assertEquals(game, completedGames);
            assertEquals(game == 5, MapSetProgressionPolicy.isComplete(completedGames, 5));
        }
        assertEquals(5, MapSetProgressionPolicy.currentGameNumber(completedGames, 5));
    }

    @Test
    void legacyIncompleteSetsKeepTheirProgress() {
        assertEquals(0, MapSetProgressionPolicy.migrate(4, 0, 5).completedGames());
        assertEquals(3, MapSetProgressionPolicy.migrate(4, 3, 5).completedGames());
    }

    @Test
    void legacyFourGameCompletionMigratesToFive() {
        MapSetProgressionPolicy.Migration migrated = MapSetProgressionPolicy.migrate(4, 4, 5);

        assertEquals(5, migrated.dataVersion());
        assertEquals(5, migrated.completedGames());
    }

    @Test
    void migrationIsIdempotent() {
        MapSetProgressionPolicy.Migration first = MapSetProgressionPolicy.migrate(4, 4, 5);
        MapSetProgressionPolicy.Migration second = MapSetProgressionPolicy.migrate(
                first.dataVersion(),
                first.completedGames(),
                5
        );

        assertEquals(first, second);
    }

    @Test
    void invalidSetLengthIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> MapSetProgressionPolicy.currentGameNumber(0, 0));
    }
}
