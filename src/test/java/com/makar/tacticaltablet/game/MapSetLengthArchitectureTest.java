package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapSetLengthArchitectureTest {

    @Test
    void allSetLengthConsumersUseFiveGameConstant() throws IOException {
        String mapSet = source("src/main/java/com/makar/tacticaltablet/game/MapSetManager.java");
        String results = source("src/main/java/com/makar/tacticaltablet/game/set/SetResultService.java");
        String discord = source(
                "src/main/java/com/makar/tacticaltablet/integration/discord/DiscordLeaderboardService.java");
        String online = source("src/main/java/com/makar/tacticaltablet/integration/online/OnlineWebhookService.java");

        assertTrue(mapSet.contains("public static final int GAMES_PER_MAP = 5;"));
        assertTrue(mapSet.contains("state.completedGames = GAMES_PER_MAP;"));
        assertFalse(mapSet.contains("Сет из 4 игр"));
        assertFalse(results.contains("fourGames"));
        assertTrue(results.contains("game <= MapSetManager.GAMES_PER_MAP"));
        assertTrue(discord.contains("currentSetMatches >= MapSetManager.GAMES_PER_MAP"));
        assertTrue(online.contains("append(MapSetManager.GAMES_PER_MAP)"));
    }

    @Test
    void migrationIsBackedUpBeforeStateNormalizationAndCommit() throws IOException {
        String mapSet = source("src/main/java/com/makar/tacticaltablet/game/MapSetManager.java");
        int backup = mapSet.indexOf("backupStateBeforeMigration(state.dataVersion)");
        int normalize = mapSet.indexOf("normalizeState();", backup);
        int save = mapSet.indexOf("saveState();", normalize);

        assertTrue(backup >= 0);
        assertTrue(normalize > backup);
        assertTrue(save > normalize);
    }

    private static String source(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
