package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class P0PerformanceArchitectureTest {
    private static String source(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/makar/tacticaltablet").resolve(relative));
    }

    @Test
    void rtpTickUsesBudgetedMaintenanceWhileTeleportKeepsFullSafetyBoundary() throws IOException {
        String safeTeleport = source("game/teleport/SafeTeleport.java");
        String tickPool = method(safeTeleport, "public static synchronized PoolStatus tickPool", "public static synchronized boolean isPoolPreparing");
        String selection = method(safeTeleport, "private static synchronized BlockPos takeBestPreparedPoint", "private static void pruneInvalidPreparedSpawns");

        assertTrue(tickPool.contains("pruneInvalidPreparedSpawnsIncrementally(overworld, PREPARED_VALIDATIONS_PER_TICK)"));
        assertFalse(tickPool.contains("pruneInvalidPreparedSpawns(overworld)"));
        assertTrue(selection.contains("pruneInvalidPreparedSpawns(overworld)"));
        assertTrue(selection.contains("validatePreparedPointForUse(overworld, position)"));
    }

    @Test
    void votingAndTeamSelectionTimerTicksUseLightweightSyncOnly() throws IOException {
        String gameState = source("game/GameStateManager.java");

        assertTrue(gameState.contains("VoteManager.tickSecond();\n        ClassXPManager.syncMatchSetupAll(server);"));
        assertTrue(gameState.contains("TeamMatchManager.tickSecond();\n        ClassXPManager.syncMatchSetupAll(server);"));
    }

    @Test
    void extractionCadenceKeepsCaptureAtTwentyHertzWithoutImplicitMembershipScan() throws IOException {
        String extraction = source("game/extraction/ExtractionPointManager.java");
        String activeTick = method(extraction, "private static void tickActive", "private static void tickEnding");
        String ensureBossbar = method(extraction, "private static void ensureBossbar", "private static void syncBossbarPlayers");
        String endingTick = method(extraction, "private static void tickEnding", "private static void updatePlayersInside");

        assertTrue(extraction.contains("PLAYER_SCAN_INTERVAL_TICKS = 2"));
        assertTrue(extraction.contains("BOSSBAR_UPDATE_INTERVAL_TICKS = 4"));
        assertTrue(extraction.contains("BOSSBAR_MEMBERSHIP_SYNC_INTERVAL_TICKS = 20"));
        assertTrue(activeTick.contains("updateCapture(server);"));
        assertFalse(ensureBossbar.contains("syncBossbarPlayers"));
        assertFalse(endingTick.contains("syncBossbarPlayers(server);"));
        assertTrue(endingTick.contains("reconcileBossbarPlayers(server);"));
    }

    private static String method(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0 && end > start, "Expected source markers were not found");
        return source.substring(start, end);
    }
}
