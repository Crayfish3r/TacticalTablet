package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatapackIndependenceArchitectureTest {
    private static final Path MAIN = Path.of("src/main/java/com/makar/tacticaltablet");

    @Test
    void matchLifecycleDoesNotCallOrPreflightWarFunctions() throws Exception {
        String gameState = Files.readString(MAIN.resolve("game/GameStateManager.java"));
        assertFalse(gameState.contains("war:start_game"));
        assertFalse(gameState.contains("war:reset"));
        assertFalse(gameState.contains("performPrefixedCommand"));
        assertFalse(gameState.contains("getFunctions()"));
        assertTrue(gameState.contains("lobby:lobby dimension is unavailable"));
        assertTrue(gameState.contains("case EXECUTE_START_DATAPACK"));
    }

    @Test
    void scoreboardOwnershipIsIdempotentAndNonDestructive() throws Exception {
        String scoreboard = Files.readString(MAIN.resolve("game/MatchScoreboard.java"));
        String lives = Files.readString(MAIN.resolve("game/lives/LivesManager.java"));
        assertTrue(scoreboard.contains("scoreboard.getObjective(name)"));
        assertTrue(scoreboard.contains("scoreboard.addObjective("));
        assertFalse(scoreboard.contains("removeObjective"));
        assertFalse(lives.contains("scoreboard objectives add"));
    }

    @Test
    void lobbyMaintenanceDoesNotDeleteEntitiesOrPlaceStructures() throws Exception {
        String lobby = Files.readString(MAIN.resolve("game/lobby/LobbyManager.java"));
        assertFalse(lobby.contains("getEntities"));
        assertFalse(lobby.contains("discard()"));
        assertFalse(lobby.contains("kill @e"));
        assertFalse(lobby.contains("placeInWorld"));
        assertTrue(lobby.contains("LobbyGameModePolicy.target"));
        assertTrue(lobby.contains("player.getY() < rescueY"));
    }

    @Test
    void reloadHasNoBootstrapOrLifecycleHook() throws Exception {
        String events = Files.readString(MAIN.resolve("game/ServerEvents.java"));
        assertTrue(events.contains("onServerStarted(ServerStartedEvent event)"));
        assertTrue(events.contains("LobbyBootstrapManager.bootstrap(event.getServer())"));
        assertTrue(events.contains("LobbyBootstrapManager.repairMissingFragileBlocks(event.getServer())"));
        assertFalse(events.contains("AddReloadListenerEvent"));
        assertFalse(events.contains("OnDatapackSyncEvent"));
    }
}
