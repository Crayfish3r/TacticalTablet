package com.makar.tacticaltablet.game;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapVoteWinnerPolicyTest {

    @Test
    void uniqueVoteLeaderWins() {
        String winner = MapVoteWinnerPolicy.selectWinner(
                List.of("Alpha", "Bravo", "Charlie"),
                Map.of("Alpha", 1, "Bravo", 3, "Charlie", 2),
                new Random(1L)
        );

        assertEquals("Bravo", winner);
    }

    @Test
    void tieWinnerAlwaysBelongsToTheLeaders() {
        Set<String> leaders = Set.of("Alpha", "Bravo");

        for (int seed = 0; seed < 20; seed++) {
            String winner = MapVoteWinnerPolicy.selectWinner(
                    List.of("Alpha", "Bravo", "Charlie"),
                    Map.of("Alpha", 2, "Bravo", 2, "Charlie", 1),
                    new Random(seed)
            );
            assertTrue(leaders.contains(winner));
        }
    }

    @Test
    void noVotesSelectsOneOfTheOfferedCandidates() {
        List<String> candidates = List.of("Alpha", "Bravo", "Charlie");

        String winner = MapVoteWinnerPolicy.selectWinner(candidates, Map.of(), new Random(7L));

        assertTrue(candidates.contains(winner));
    }

    @Test
    void emptyCandidatesHaveNoWinner() {
        assertEquals("", MapVoteWinnerPolicy.selectWinner(List.of(), Map.of(), new Random(1L)));
    }
}
