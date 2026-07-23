package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapVoteLifecycleArchitectureTest {

    private static final Path MAP_SET_MANAGER =
            Path.of("src/main/java/com/makar/tacticaltablet/game/MapSetManager.java");

    @Test
    void playedHistoryChangesOnlyWhenACompletedMapStartsItsNextVote() throws IOException {
        String source = Files.readString(MAP_SET_MANAGER);
        String serverStart = block(source, "public static synchronized void onServerStarted", "public static synchronized void onServerStopped");
        String startVoting = block(source, "public static synchronized void startVoting", "public static synchronized void vote");
        String tickVoting = block(source, "public static synchronized VoteTickResult tickVoting", "public static synchronized void tickRestart");

        assertTrue(serverStart.contains("reconcileRecentPlayedMaps(server)"));
        assertTrue(startVoting.contains("if (!debug && isSetComplete())"));
        assertTrue(startVoting.contains("MapVoteCandidatePolicy.recordPlayedMap"));
        assertFalse(tickVoting.contains("recordPlayedMap"));
        assertFalse(tickVoting.contains("recentPlayedMaps"));
    }

    @Test
    void rotationRetryKeepsCandidatesVotesAndResolvedWinner() throws IOException {
        String source = Files.readString(MAP_SET_MANAGER);
        String tickVoting = block(source, "public static synchronized VoteTickResult tickVoting", "public static synchronized void tickRestart");
        String failure = block(tickVoting, "catch (IOException | RuntimeException exception)", "voting = false");

        assertTrue(tickVoting.contains("selectedMap.isBlank() ? resolveWinner() : selectedMap"));
        assertFalse(failure.contains("votingMaps ="));
        assertFalse(failure.contains("votes.clear()"));
        assertFalse(failure.contains("selectedMap = \"\""));
    }

    @Test
    void serverCanonicalizesVotesOnlyAgainstFrozenCandidates() throws IOException {
        String source = Files.readString(MAP_SET_MANAGER);
        String vote = block(source, "public static synchronized void vote", "public static synchronized void setNextSetCompetitive");
        String canonical = block(source, "private static String canonicalMapName", "private static void initStorage");

        assertTrue(vote.contains("if (canonical == null) return"));
        assertTrue(canonical.contains("MapVoteCandidatePolicy.canonicalMapName(votingMaps, value)"));
    }

    private static String block(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        if (start < 0 || end < 0) {
            throw new AssertionError("Could not find source block: " + startMarker + " -> " + endMarker);
        }
        return source.substring(start, end);
    }
}
