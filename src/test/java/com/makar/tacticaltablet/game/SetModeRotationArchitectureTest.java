package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetModeRotationArchitectureTest {
    private static final Path MAIN = Path.of("src/main/java/com/makar/tacticaltablet");

    @Test
    void voteAvailabilityIsSnapshottedAndRevalidatedOnServer() throws Exception {
        String manager = read("game/MapSetManager.java");
        String opening = block(manager, "public static synchronized void startVoting",
                "public static synchronized void vote(");
        String vote = block(manager, "public static synchronized void voteSetMode",
                "public static synchronized void setNextSetCompetitive");

        assertTrue(opening.contains("SetModeRotationPolicy.availableModes("));
        assertTrue(opening.contains("GameStateManager.getMatchParticipantCandidateCount(server)"));
        assertTrue(vote.contains("SetModeRotationPolicy.isAvailable(availableSetModes, mode)"));
        assertTrue(vote.contains("sync(player, false)"));
    }

    @Test
    void winnerAndClientStateUseTheSameAvailabilitySnapshot() throws Exception {
        String manager = read("game/MapSetManager.java");
        assertTrue(manager.contains("SetModeVotePolicy.selectWinner(\n                    modeVoteCounts(), availableSetModes, RANDOM)"));
        assertTrue(manager.contains("SetModeRotationPolicy.availabilityMask(availableSetModes)"));
    }

    @Test
    void completedSetUpdatesRotationThroughOneGuardedHelper() throws Exception {
        String manager = read("game/MapSetManager.java");
        assertEquals(1, count(manager, "SetModeRotationPolicy.recordCompletedSet("));
        String helper = block(manager, "private static void recordRotationIfSetJustCompleted",
                "private static void broadcast");
        assertTrue(helper.contains("SetModeRotationPolicy.shouldRecordCompletion("));
    }

    @Test
    void competitiveGateRunsOnceBeforeFirstGameAndUsesFallback() throws Exception {
        String game = read("game/GameStateManager.java");
        String start = block(game, "public static void startGame", "private static void recoverAfterFailedStart");
        String gate = block(game, "private static void tickCompetitivePreStart",
                "private static boolean isCompetitivePreStartCheckpoint");

        assertTrue(start.contains("MapSetManager.getCompletedGames() == 0"));
        assertTrue(start.contains("COMPETITIVE_PRESTART_SECONDS"));
        assertTrue(gate.contains("getMatchParticipantCandidateCount(server)"));
        assertTrue(gate.contains("MapSetManager.fallbackCompetitiveToCasual(server)"));
        assertTrue(gate.contains("startGameNow(server)"));
        assertFalse(gate.contains("Thread.sleep"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(MAIN.resolve(relative)).replace("\r\n", "\n");
    }

    private static String block(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0, startMarker);
        assertTrue(end > start, endMarker);
        return source.substring(start, end);
    }

    private static int count(String source, String needle) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(needle, index)) >= 0; index += needle.length()) count++;
        return count;
    }
}
