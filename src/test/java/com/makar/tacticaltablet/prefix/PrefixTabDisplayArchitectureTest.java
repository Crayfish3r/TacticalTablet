package com.makar.tacticaltablet.prefix;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrefixTabDisplayArchitectureTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");

    @Test
    void tabBuilderUsesAuthoritativeScoreboardTeamAndChatBuilderRemainsSeparate() throws IOException {
        String prefixManager = read("com/makar/tacticaltablet/prefix/PrefixManager.java");

        assertTrue(prefixManager.contains(
                "player.getScoreboard().getPlayersTeam(player.getScoreboardName())"));
        assertTrue(prefixManager.contains(
                "team == null ? ChatFormatting.RESET : team.getColor()"));
        assertTrue(prefixManager.contains(
                "PrefixDisplayHelper.buildTabName(cleanPlayerName(player), teamColor, getRole(player))"));
        assertTrue(prefixManager.contains(
                "return PrefixDisplayHelper.appendSuffix(cleanPlayerName(player), getRole(player));"));
    }

    @Test
    void vanillaTabFlowIsLeftAloneWithoutVisiblePrefix() throws IOException {
        String events = read("com/makar/tacticaltablet/game/ServerEvents.java");
        String tabHandler = between(events,
                "public static void onTabListNameFormat",
                "private static void syncPrefixes");

        assertTrue(tabHandler.contains("if (!PrefixManager.getRole(player).visible()) return;"));
        assertTrue(tabHandler.contains("PrefixManager.buildTabDisplayName(player)"));
    }

    @Test
    void teamColorCannotBeAppliedToBadgeContainer() throws IOException {
        String helper = read("com/makar/tacticaltablet/prefix/PrefixDisplayHelper.java");
        String tabBuilder = between(helper,
                "public static Component buildTabName(Component playerName, ChatFormatting teamColor, Component badge)",
                "public static Component plainSystem");

        assertTrue(tabBuilder.contains("MutableComponent result = Component.empty();"));
        assertTrue(tabBuilder.contains("styledPlayerName.withStyle(teamColor);"));
        assertFalse(tabBuilder.contains("result.withStyle(teamColor)"));
        assertFalse(tabBuilder.contains("badge.withStyle(teamColor)"));
    }

    @Test
    void scoreboardMembershipChangesRefreshTabWithoutPrefixDependency() throws IOException {
        String teams = read("com/makar/tacticaltablet/game/team/TeamMatchManager.java");
        String apply = between(teams,
                "public static void applyScoreboardTeams",
                "public static void cleanupScoreboardTeams");
        String cleanup = between(teams,
                "public static void cleanupScoreboardTeams",
                "public static int getAliveTeamCount");

        assertTrue(apply.contains("boolean membershipChanged = false;"));
        assertTrue(apply.contains("player.refreshTabListName();"));
        assertTrue(cleanup.contains("boolean membershipChanged = false;"));
        assertTrue(cleanup.contains("player.refreshTabListName();"));
        assertFalse(teams.contains("PrefixManager"));
    }

    @Test
    void prefixRoleCommandsStillRefreshTabNames() throws IOException {
        String command = read("com/makar/tacticaltablet/prefix/PrefixCommand.java");

        assertTrue(command.contains("PrefixManager.setRole(target.uuid(), target.name(), role, 0L);"));
        assertTrue(command.contains("PrefixManager.clearRole(target.uuid());"));
        assertTrue(command.contains("PrefixManager.updateTabNames(source.getServer());"));
    }

    @Test
    void projectDoesNotAddScoreboardPrefixOrSuffixToCustomTabName() throws IOException {
        String teams = read("com/makar/tacticaltablet/game/team/TeamMatchManager.java");
        String helper = read("com/makar/tacticaltablet/prefix/PrefixDisplayHelper.java");

        assertFalse(teams.contains("setPlayerPrefix"));
        assertFalse(teams.contains("setPlayerSuffix"));
        assertFalse(helper.contains("getPlayerPrefix"));
        assertFalse(helper.contains("getPlayerSuffix"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(MAIN_JAVA.resolve(relativePath));
    }

    private static String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0, "Missing start marker: " + startMarker);
        assertTrue(end > start, "Missing end marker: " + endMarker);
        return source.substring(start, end);
    }
}
