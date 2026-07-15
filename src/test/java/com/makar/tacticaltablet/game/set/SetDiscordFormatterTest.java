package com.makar.tacticaltablet.game.set;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetDiscordFormatterTest {
    @Test
    void tableUsesSnapshotOrderAndShowsAllRequiredScoringColumns() {
        SetPlayerResult first = result(new UUID(0, 1), "First", 1, 2, 1, 40, 1);
        SetPlayerResult second = result(new UUID(0, 2), "Second", 2, 1, 3, 20, 2);
        SetLeaderboardSnapshot snapshot = new SetLeaderboardSnapshot(UUID.randomUUID(), 2, List.of(first, second));

        String discord = SetDiscordFormatter.formatLeaderboard(snapshot, 4000);

        assertTrue(discord.indexOf("First") < discord.indexOf("Second"));
        assertTrue(discord.contains("очк."));
        assertTrue(discord.contains("W:"));
        assertTrue(discord.contains("K:"));
        assertTrue(discord.contains("A:"));
        assertTrue(discord.contains("DMG:"));
        assertTrue(discord.contains("D:"));
        assertTrue(discord.contains("Placement:"));
    }

    @Test
    void longLeaderboardIsSplitWithoutExceedingDiscordDescriptionLimitOrDroppingRows() {
        List<SetPlayerResult> players = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            players.add(result(new UUID(0, i + 1), "LongPlayerName_" + i, 8, i, i, 200 + i, i));
        }
        List<String> pages = SetDiscordFormatter.formatLeaderboardPages(
                new SetLeaderboardSnapshot(UUID.randomUUID(), players.size(), players), 900);

        assertTrue(pages.size() > 1);
        assertTrue(pages.stream().allMatch(page -> page.length() <= 900));
        String joined = String.join("", pages);
        for (int i = 0; i < 120; i++) assertTrue(joined.contains("LongPlayerName_" + i));
    }

    private static SetPlayerResult result(UUID id, String name, int placement, int kills,
                                          int assists, double damage, int deaths) {
        GamePerformance game = new GamePerformance(1, id, name, placement, kills, assists, damage, deaths, "");
        return SetPlayerResult.fromGames(id, name, List.of(game));
    }
}
