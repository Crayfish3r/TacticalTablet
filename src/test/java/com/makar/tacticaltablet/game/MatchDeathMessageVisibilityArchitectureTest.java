package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchDeathMessageVisibilityArchitectureTest {
    private static final Path MAIN = Path.of("src/main/java/com/makar/tacticaltablet");

    @Test
    void tacticalTeamAndSoloTeamsSuppressVanillaDeathMessagesOnlyWhileManaged() throws IOException {
        String team = Files.readString(MAIN.resolve("game/team/TeamMatchManager.java"));
        String solo = Files.readString(MAIN.resolve("client/NameTagManager.java"));

        assertTrue(team.contains("team.setDeathMessageVisibility(Team.Visibility.NEVER)"));
        assertTrue(solo.contains("team.setDeathMessageVisibility(Team.Visibility.NEVER)"));
        assertFalse(team.contains("setDeathMessageVisibility(Team.Visibility.ALWAYS)"));
        assertFalse(solo.contains("setDeathMessageVisibility(Team.Visibility.ALWAYS)"));
        assertTrue(team.contains("cleanupScoreboardTeams"));
        assertTrue(team.contains("originalScoreboardTeams"));
        assertTrue(solo.contains("if (!GameStateManager.isRunning(player.server)) return false"));
        assertTrue(solo.contains("removeFromManagedTeam(scoreboard, player)"));
    }
}
