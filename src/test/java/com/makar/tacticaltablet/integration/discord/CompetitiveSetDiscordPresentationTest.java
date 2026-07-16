package com.makar.tacticaltablet.integration.discord;

import com.makar.tacticaltablet.game.set.SetPlacement;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompetitiveSetDiscordPresentationTest {

    @Test
    void competitivePlacementKeepsLeaderboardFactsWithoutFictitiousCoins() {
        String line = DiscordLeaderboardService.formatSetPlacement(placement(), 100, true);

        assertTrue(line.contains("1. **Winner**"));
        assertTrue(line.contains("Очки `42`"));
        assertFalse(line.toLowerCase(java.util.Locale.ROOT).contains("coin"));
    }

    @Test
    void casualPlacementStillShowsItsExistingReward() {
        String line = DiscordLeaderboardService.formatSetPlacement(placement(), 35, false);

        assertTrue(line.contains("35 coins"));
    }

    @Test
    void casualPodiumLinesShowTheAmountPassedForEachPlace() {
        assertTrue(DiscordLeaderboardService.formatSetPlacement(placement(1, "First"), 58, false)
                .contains("1. **First** — `58 coins`"));
        assertTrue(DiscordLeaderboardService.formatSetPlacement(placement(2, "Second"), 31, false)
                .contains("2. **Second** — `31 coins`"));
        assertTrue(DiscordLeaderboardService.formatSetPlacement(placement(3, "Third"), 16, false)
                .contains("3. **Third** — `16 coins`"));
    }

    private static SetPlacement placement() {
        return placement(1, "Winner");
    }

    private static SetPlacement placement(int place, String name) {
        return new SetPlacement(place, UUID.randomUUID(), name, 42, 2, 3, 4, 55.5D, 1);
    }
}
