package com.makar.tacticaltablet.integration.discord;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetScoringIntegrationArchitectureTest {
    @Test
    void rewardsPodiumAndDiscordSharePersistedLeaderboardSnapshot() throws IOException {
        String discord = source("src/main/java/com/makar/tacticaltablet/integration/discord/DiscordLeaderboardService.java");
        String result = source("src/main/java/com/makar/tacticaltablet/game/set/SetResultService.java");
        String mapSet = source("src/main/java/com/makar/tacticaltablet/game/MapSetManager.java");

        assertTrue(discord.contains("SetResultService.createSnapshot"));
        assertTrue(discord.contains("SetResultService.createRewardSummary(\n                snapshot, MapSetManager.isCompetitiveSet())"));
        assertTrue(discord.contains("MapSetManager.saveCompletedSetResults(server, snapshot, summary)"));
        assertTrue(discord.contains("SetLeaderboardSnapshot snapshot = MapSetManager.getLeaderboardSnapshot()"));
        assertTrue(result.contains("SetScoringRules.SET_RESULT_COMPARATOR"));
        assertTrue(mapSet.contains("SetLeaderboardSnapshot leaderboardSnapshot"));
        assertFalse(discord.contains("rankedSetStats"));
    }

    @Test
    void assistsUseTheSameAcceptedEffectivePvpDamagePath() throws IOException {
        String events = source("src/main/java/com/makar/tacticaltablet/game/ServerEvents.java");
        int acceptedDamage = events.indexOf("MatchDamageAccounting.shouldRecordDamage");
        int damageStat = events.indexOf("DiscordLeaderboardService.recordMatchDamage", acceptedDamage);
        int assistAttribution = events.indexOf("SetMatchRuntime.recordEffectivePvpDamage", acceptedDamage);

        assertTrue(acceptedDamage >= 0);
        assertTrue(damageStat > acceptedDamage);
        assertTrue(assistAttribution > damageStat);
        assertTrue(events.contains("SetMatchRuntime.resolveAssists"));
        assertTrue(events.contains("SetMatchRuntime.clearVictimAttribution"));
    }

    private static String source(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
