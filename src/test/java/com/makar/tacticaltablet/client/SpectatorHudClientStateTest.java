package com.makar.tacticaltablet.client;

import com.makar.tacticaltablet.game.SpectatorHudSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectatorHudClientStateTest {
    @AfterEach
    void clearState() {
        SpectatorHudClientState.clear();
    }

    @Test
    void replacesTargetAndClearsServerSnapshot() {
        SpectatorHudClientState.update(new SpectatorHudSnapshot("First", "Class A", 1, 2, 3, 4));
        SpectatorHudClientState.update(new SpectatorHudSnapshot("Second", "Class B", 5, 6, 7, 8));

        assertEquals("Second", SpectatorHudClientState.snapshot().orElseThrow().playerName());
        SpectatorHudClientState.clear();
        assertTrue(SpectatorHudClientState.snapshot().isEmpty());
    }
}
