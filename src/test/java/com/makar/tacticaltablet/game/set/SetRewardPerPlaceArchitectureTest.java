package com.makar.tacticaltablet.game.set;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetRewardPerPlaceArchitectureTest {
    private static final Path MAIN_JAVA = Path.of("src/main/java/com/makar/tacticaltablet");

    @Test
    void awardAndPresentationsReadSavedAmountForEachPlace() throws IOException {
        String award = source("game/set/SetRewardService.java");
        String presentation = source("game/set/SetRewardPresentation.java");
        String discord = source("integration/discord/DiscordLeaderboardService.java");

        assertTrue(award.contains("summary.coinsForPlace(placement.place())"));
        assertTrue(presentation.contains("summary.coinsForPlace(placement.place())"));
        assertTrue(discord.contains("summary.coinsForPlace(placement.place())"));
        assertFalse(award.contains("summary.rewardCoins()"));
        assertFalse(presentation.contains("summary.rewardCoins()"));
        assertFalse(discord.contains("summary.rewardCoins()"));
    }

    @Test
    void payoutIsCalculatedOnceWhenImmutableSummaryIsCreated() throws IOException {
        String result = source("game/set/SetResultService.java");
        String award = source("game/set/SetRewardService.java");
        String presentation = source("game/set/SetRewardPresentation.java");
        String discord = source("integration/discord/DiscordLeaderboardService.java");

        assertTrue(result.contains("SetRewardDistribution.distribute(reward, count)"));
        assertTrue(result.contains("SetRewardSummary.withPerPlacePayouts("));
        assertFalse(award.contains("SetRewardDistribution"));
        assertFalse(presentation.contains("SetRewardDistribution"));
        assertFalse(discord.contains("SetRewardDistribution"));
    }

    @Test
    void idempotencyIdentityExcludesAmountAndFailuresLogActualAmount() throws IOException {
        String award = source("game/set/SetRewardService.java");
        String gameState = source("game/GameStateManager.java");

        assertTrue(award.contains("set:\" + setId + \":place:\" + placement.place() + \":player:\" + placement.playerId()"));
        assertFalse(block(award, "public static String idempotencyKey", "public static Set<Integer>")
                .contains("coins"));
        assertTrue(gameState.contains("place {} ({} coins)"));
        assertTrue(gameState.contains("payout.placement().place(), payout.coins()"));
    }

    @Test
    void offlinePersistenceAndSummarySaveRemainAheadOfPayoutPresentation() throws IOException {
        String award = source("game/set/SetRewardService.java");
        String discord = source("integration/discord/DiscordLeaderboardService.java");

        assertTrue(award.contains("PlayerProgressManager.applyIdempotentCoinCredit("));
        String prepare = block(discord, "public static synchronized SetRewardSummary prepareCompletedSetSummary",
                "public static synchronized CompletableFuture<Boolean> sendCompletedSetReport");
        assertTrue(prepare.contains("MapSetManager.saveCompletedSetResults(server, snapshot, summary)"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(MAIN_JAVA.resolve(relativePath)).replace("\r\n", "\n");
    }

    private static String block(String source, String startNeedle, String endNeedle) {
        int start = source.indexOf(startNeedle);
        int end = source.indexOf(endNeedle, start);
        assertTrue(start >= 0, startNeedle);
        assertTrue(end > start, endNeedle);
        return source.substring(start, end);
    }
}
