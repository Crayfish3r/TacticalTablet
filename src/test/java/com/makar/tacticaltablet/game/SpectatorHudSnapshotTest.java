package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.stats.PlayerStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpectatorHudSnapshotTest {
    @Test
    void sanitizesAndFormatsDisplayStatistics() {
        SpectatorHudSnapshot snapshot = SpectatorHudSnapshot.from(
                new PlayerStats("Player\nName", 7, 2, 3, 9, 500), "Снайпер");

        assertEquals("Player_Name", snapshot.playerName());
        assertEquals("Снайпер", snapshot.className());
        assertEquals("3.50", snapshot.formattedKd());
        assertEquals(7, snapshot.kills());
        assertEquals(9, snapshot.matchesPlayed());
    }

    @Test
    void clampsUnavailableStatisticsToZero() {
        SpectatorHudSnapshot snapshot = new SpectatorHudSnapshot("P", "", -1, -2, -3, -4);
        assertEquals(0, snapshot.kills());
        assertEquals(0, snapshot.deaths());
        assertEquals("0.00", snapshot.formattedKd());
    }
}
