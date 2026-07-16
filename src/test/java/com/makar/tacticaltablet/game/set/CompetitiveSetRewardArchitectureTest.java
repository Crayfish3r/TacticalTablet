package com.makar.tacticaltablet.game.set;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CompetitiveSetRewardArchitectureTest {
    private static final Path MAIN_JAVA = Path.of("src/main/java/com/makar/tacticaltablet");

    @Test
    void competitiveGuardRunsBeforeIdempotencyKeyAndOfflinePersistence() throws IOException {
        String rewardService = source("game/set/SetRewardService.java");

        int guard = rewardService.indexOf("!shouldAwardFinalCoins(competitiveSet, summary)");
        int key = rewardService.indexOf("idempotencyKey(summary.setId(), placement)");
        int credit = rewardService.indexOf("PlayerProgressManager.applyIdempotentCoinCredit(");
        assertTrue(guard >= 0 && guard < key && key < credit);
    }

    @Test
    void completionPassesAuthoritativeCompetitiveModeToSummaryAndPayout() throws IOException {
        String discord = source("integration/discord/DiscordLeaderboardService.java");
        String gameState = source("game/GameStateManager.java");

        assertTrue(discord.contains("snapshot, MapSetManager.isCompetitiveSet())"));
        assertTrue(gameState.contains("server, summary, MapSetManager.isCompetitiveSet())"));
    }

    @Test
    void killCasualAndClanWarCoinPathsRemainPresent() throws IOException {
        String progress = source("progression/PlayerProgressManager.java");
        String events = source("game/ServerEvents.java");
        String rewardService = source("game/set/SetRewardService.java");
        String discord = source("integration/discord/DiscordLeaderboardService.java");

        assertTrue(progress.contains("public static final int KILL_COIN_REWARD = 5;"));
        assertTrue(events.contains("PlayerProgressManager.addCoins(killer, PlayerProgressManager.KILL_COIN_REWARD);"));
        assertTrue(rewardService.contains("return participantCount < 2 ? 0 : 15 + Math.max(0, participantCount - 2) * 5;"));
        assertTrue(discord.contains("ClanManager.addClanCoins(server, clanWinner.clanId, CLAN_WAR_WIN_CLAN_COINS);"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(MAIN_JAVA.resolve(relativePath)).replace("\r\n", "\n");
    }
}
