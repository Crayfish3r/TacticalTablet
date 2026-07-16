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

    private static SetPlacement placement() {
        return new SetPlacement(1, UUID.randomUUID(), "Winner", 42, 2, 3, 4, 55.5D, 1);
    }
}
