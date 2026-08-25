package com.makar.tacticaltablet.game;

import com.makar.tacticaltablet.game.lifecycle.integration.MatchStartRecoveryPolicy;
import com.makar.tacticaltablet.game.lifecycle.integration.MatchStartStatus;
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
        assertTrue(source.contains("MatchStartResult result = MATCH_START_COORDINATOR.start(server);"));
        assertTrue(source.contains("handleStartResult(server, result);"));
        assertTrue(source.contains("recoverAfterFailedStart(server, result.status())"));
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
    void criticalStartStepsUseJavaOwnedStateWithoutDatapackCommands() throws IOException {
        String source = readSource();

        assertFalse(source.contains("war:start_game"));
        assertFalse(source.contains("war:reset"));
        assertFalse(source.contains("performPrefixedCommand"));
        assertTrue(source.contains("MatchScoreboard.ensureObjectives(server)"));
        assertTrue(source.contains("legacy scoreboard did not commit RUNNING state"));
        assertTrue(source.contains("legacy scoreboard did not rollback to WAITING state"));
    }


    @Test
    void unsuccessfulTerminalStartsRecoverTheLegacyWaitingPhase() {
        assertTrue(MatchStartRecoveryPolicy.shouldRecover(MatchStartStatus.REJECTED));
        assertTrue(MatchStartRecoveryPolicy.shouldRecover(MatchStartStatus.FAILED_ROLLED_BACK));
        assertTrue(MatchStartRecoveryPolicy.shouldRecover(MatchStartStatus.FAILED_REQUIRES_CLEANUP));
        assertTrue(MatchStartRecoveryPolicy.shouldRecover(MatchStartStatus.BLOCKED_REQUIRES_CLEANUP));
        assertFalse(MatchStartRecoveryPolicy.shouldRecover(MatchStartStatus.STARTED));
        assertFalse(MatchStartRecoveryPolicy.shouldRecover(MatchStartStatus.ALREADY_STARTING));
        assertFalse(MatchStartRecoveryPolicy.shouldRecover(MatchStartStatus.ALREADY_RUNNING));
        assertFalse(MatchStartRecoveryPolicy.shouldRecover(MatchStartStatus.STALE_OPERATION));
    }

    @Test
    void highRiskStartGuardsRemainWiredIntoProductionPaths() throws IOException {
        String source = readSource();
        String zone = Files.readString(Path.of("src/main/java/com/makar/tacticaltablet/game/zone/ZoneManager.java"));
        String clan = Files.readString(Path.of("src/main/java/com/makar/tacticaltablet/game/clanwar/ClanWarManager.java"));
        String command = Files.readString(Path.of("src/main/java/com/makar/tacticaltablet/command/TestModeCommand.java"));

        assertTrue(source.contains("ZoneManager.validateConfiguredRtpSettings(server)"));
        assertTrue(zone.contains("throw new IllegalStateException(\"Invalid RTP configuration: \""));
        assertTrue(source.contains("ClanWarManager.getParticipantCandidateClanCount(server) < 2"));
        assertTrue(clan.contains("soloDebug = false;"));
        assertTrue(command.contains("GameStateManager.forceStartClanWarDebug"));
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
