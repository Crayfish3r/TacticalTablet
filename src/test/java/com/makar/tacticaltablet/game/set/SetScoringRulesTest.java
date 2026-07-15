package com.makar.tacticaltablet.game.set;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SetScoringRulesTest {
    @Test
    void placementPointsUseSinglePublishedTable() {
        assertEquals(List.of(0, 0, 12, 9, 7, 5, 4, 3, 2, 1, 1),
                List.of(-1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 20).stream()
                        .map(SetScoringRules::getPlacementPoints).toList());
    }

    @Test
    void gameScoreCombinesPlacementKillsAssistsAndFlooredDamage() {
        assertEquals(12, score(1, 0, 0, 0));
        assertEquals(16, score(2, 2, 1, 40));
        assertEquals(18, score(8, 5, 2, 119));
        assertEquals(0, score(0, 0, 0, 0));
        assertEquals(List.of(0, 1, 1, 2), List.of(19.99, 20.0, 39.99, 40.0).stream()
                .map(SetScoringRules::getDamagePoints).toList());
    }

    @Test
    void comparatorUsesEveryTieBreakerAndScoreBeatsWins() {
        UUID low = new UUID(0, 1);
        UUID high = new UUID(0, 2);
        SetPlayerResult winnerByOldRules = result(low, "A", 25, 1, 1, 100, 1);
        SetPlayerResult combatLeader = result(high, "B", 45, 0, 8, 400, 5);
        assertEquals(List.of("B", "A"), java.util.stream.Stream.of(winnerByOldRules, combatLeader)
                .sorted(SetScoringRules.SET_RESULT_COMPARATOR).map(SetPlayerResult::playerName).toList());

        List<SetPlayerResult> ordered = List.of(
                result(new UUID(0, 7), "zulu", 10, 2, 4, 100, 2),
                result(new UUID(0, 6), "Alpha", 10, 2, 4, 100, 2),
                result(new UUID(0, 5), "Deaths", 10, 2, 4, 100, 3),
                result(new UUID(0, 4), "Damage", 10, 2, 4, 90, 1),
                result(new UUID(0, 3), "Kills", 10, 2, 3, 500, 0),
                result(new UUID(0, 2), "Wins", 10, 1, 99, 999, 0),
                result(new UUID(0, 1), "Score", 9, 99, 99, 999, 0));
        assertEquals(List.of("Alpha", "zulu", "Deaths", "Damage", "Kills", "Wins", "Score"),
                ordered.stream().sorted(SetScoringRules.SET_RESULT_COMPARATOR)
                        .map(SetPlayerResult::playerName).toList());

        SetPlayerResult uuidFirst = result(new UUID(0, 1), "same", 10, 0, 0, 0, 0);
        SetPlayerResult uuidSecond = result(new UUID(0, 2), "SAME", 10, 0, 0, 0, 0);
        assertEquals(List.of(uuidFirst, uuidSecond), java.util.stream.Stream.of(uuidSecond, uuidFirst)
                .sorted(SetScoringRules.SET_RESULT_COMPARATOR).toList());
    }

    private static int score(int placement, int kills, int assists, double damage) {
        return SetScoringRules.calculateGameScore(new GamePerformance(1, UUID.randomUUID(), "P",
                placement, kills, assists, damage, 0, ""));
    }

    private static SetPlayerResult result(UUID id, String name, int score, int wins, int kills,
                                          double damage, int deaths) {
        GamePerformance scoringGame = new GamePerformance(1, id, name, 0, score / 2, score % 2, 0, 0, "");
        return new SetPlayerResult(id, name, score, wins, kills, 0, damage, deaths, List.of(scoringGame));
    }
}
