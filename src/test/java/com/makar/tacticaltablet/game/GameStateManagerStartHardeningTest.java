package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameStateManagerStartHardeningTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/makar/tacticaltablet/game/GameStateManager.java"
    );

    @Test
    void startFacadeDoesNotKeepLegacyCoordinatorFallback() throws IOException {
        String source = readSource();

        assertFalse(source.contains("startGameThroughCoordinator"));
        assertTrue(source.contains("handleStartResult(server, MATCH_START_COORDINATOR.start(server));"));
        assertFalse(facadeBlock(source).contains("Р"));
    }

    @Test
    void matchPlayedIsRecordedOnlyAfterStartCommit() throws IOException {
        String source = readSource();

        int postCommit = source.indexOf("public void postCommit(MinecraftServer server) throws Exception");
        int initializePlayers = source.indexOf("private void initializePlayers(MinecraftServer server)");
        int rollbackPlayers = source.indexOf("private void rollbackPlayers(MinecraftServer server)");
        int addMatchPlayed = source.indexOf("PlayerProgressManager.ensureMatchPlayed(player, matchId, null)");

        assertTrue(postCommit >= 0);
        assertTrue(initializePlayers >= 0);
        assertTrue(rollbackPlayers > initializePlayers);
        assertTrue(addMatchPlayed > postCommit);
        assertFalse(source.substring(initializePlayers, rollbackPlayers).contains("ensureMatchPlayed"));
        assertFalse(postCommitBlock(source).contains("Р"));
    }

    @Test
    void criticalStartStepsCheckActualCommandAndLegacyStateResults() throws IOException {
        String source = readSource();

        assertTrue(source.contains("requireDatapackCommandSuccess(START_GAME_FUNCTION, result)"));
        assertTrue(source.contains("requireDatapackCommandSuccess(RESET_GAME_FUNCTION, result)"));
        assertTrue(source.contains("legacy scoreboard did not commit RUNNING state"));
        assertTrue(source.contains("legacy scoreboard did not rollback to WAITING state"));
    }

    private static String readSource() throws IOException {
        return Files.readString(SOURCE);
    }

    private static String facadeBlock(String source) {
        int start = source.indexOf("public static void startGame(MinecraftServer server)");
        int end = source.indexOf("private static String clanWarWinnerLabel", start);
        assertTrue(start >= 0);
        assertTrue(end > start);
        return source.substring(start, end);
    }

    private static String postCommitBlock(String source) {
        int start = source.indexOf("public void postCommit(MinecraftServer server) throws Exception");
        int end = source.indexOf("private void initializePlayers(MinecraftServer server)", start);
        assertTrue(start >= 0);
        assertTrue(end > start);
        return source.substring(start, end);
    }
}
