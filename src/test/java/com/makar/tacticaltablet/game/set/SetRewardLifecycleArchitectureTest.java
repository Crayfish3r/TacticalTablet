package com.makar.tacticaltablet.game.set;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SetRewardLifecycleArchitectureTest {
    private static final Path GAME = Path.of("src/main/java/com/makar/tacticaltablet/game/GameStateManager.java");
    private static final Path MAP_SET = Path.of("src/main/java/com/makar/tacticaltablet/game/MapSetManager.java");
    private static final Path RTP = Path.of("src/main/java/com/makar/tacticaltablet/game/respawn/RtpTimerManager.java");

    @Test
    void bothCompletedSetTransitionsGoThroughRewardingBeforeMapVote() throws IOException {
        String source = Files.readString(GAME);
        String postGame = block(source, "if (postGameDelay > 0)", "if (!isRunning(server))");
        String waiting = block(source, "if (matchPhase == MatchPhase.WAITING && MapSetManager.isSetComplete())", "if (matchPhase == MatchPhase.VOTING)");

        assertTrue(postGame.contains("beginSetRewarding(server)"));
        assertTrue(waiting.contains("beginSetRewarding(server)"));
        assertTrue(waiting.contains("wasRewardingCompleted()"));
    }

    @Test
    void rewardingUsesFifteenSecondsAndMapVoteKeepsThirtySeconds() throws IOException {
        String mapSet = Files.readString(MAP_SET);
        String game = Files.readString(GAME);
        String rewardingTick = block(game, "if (matchPhase == MatchPhase.SET_REWARDING)", "if (matchPhase == MatchPhase.WAITING");

        assertTrue(mapSet.contains("SET_REWARD_SECONDS = 15"));
        assertTrue(mapSet.contains("MAP_VOTE_SECONDS = 30"));
        assertTrue(rewardingTick.contains("SET_REWARD_COUNTDOWN.tickSecond()"));
        assertTrue(rewardingTick.contains("beginMapVoting(server, false)"));
    }

    @Test
    void participantsAreRecordedOnlyAtActiveCaptureOrSuccessfulRtp() throws IOException {
        String game = Files.readString(GAME);
        String rtp = Files.readString(RTP);

        assertTrue(game.contains("player.getTags().contains(\"war.playing\") && LivesManager.isAliveParticipant(player)"));
        assertTrue(rtp.indexOf("player.addTag(\"war.playing\")")
                < rtp.indexOf("MapSetManager.recordParticipant"));
    }

    @Test
    void debugMapVoteExplicitlySkipsRatherThanPaysSetReward() throws IOException {
        String source = Files.readString(MAP_SET);
        String debugSkip = block(source, "public static synchronized void skipRewardingForDebug", "public static synchronized boolean isSetReportDispatched");

        assertTrue(debugSkip.contains("RewardPhaseStatus.SKIPPED"));
        assertTrue(debugSkip.contains("state.rewardSummary = null"));
        assertTrue(!debugSkip.contains("SetRewardService"));
    }

    private static String block(String source, String startNeedle, String endNeedle) {
        int start = source.indexOf(startNeedle);
        int end = source.indexOf(endNeedle, start);
        assertTrue(start >= 0, startNeedle);
        assertTrue(end > start, endNeedle);
        return source.substring(start, end);
    }
}
