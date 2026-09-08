package com.makar.tacticaltablet.casino;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CasinoSessionArchitectureTest {
    private static final Path MAIN = Path.of("src/main/java/com/makar/tacticaltablet");

    @Test
    void delayedReturnRemainsExcludedAndCanBeCancelledByReentry() throws IOException {
        String session = source("casino/CasinoSessionManager.java");
        String lobby = source("game/lobby/LobbyManager.java");

        assertTrue(session.contains("public static final int RETURN_DELAY_TICKS = 20 * 10"));
        assertTrue(session.contains("SESSIONS.containsKey(playerId) || PENDING_RETURNS.containsKey(playerId)"));
        assertTrue(session.contains("PENDING_RETURNS.remove(player.getUUID())"));
        assertTrue(session.contains("currentTick >= entry.getValue().deadlineTick"));
        assertTrue(lobby.contains("!CasinoSessionManager.isExcludedFromMatch(player)"));
    }

    @Test
    void activeSoloReturnUsesAdmissionAndConsumesExactlyOneLife() throws IOException {
        String session = source("casino/CasinoSessionManager.java");

        assertTrue(session.contains("MatchAdmissionManager.finalizePlayerJoin(player)"));
        assertTrue(session.contains("if (mode == MatchMode.SOLO)"));
        assertTrue(session.contains("remainingLives = Math.max(0, remainingLives - 1)"));
        assertTrue(session.contains("LivesManager.setLives(player, remainingLives)"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(MAIN.resolve(relativePath)).replace("\r\n", "\n");
    }
}