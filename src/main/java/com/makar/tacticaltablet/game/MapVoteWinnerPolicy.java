package com.makar.tacticaltablet.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class MapVoteWinnerPolicy {

    private MapVoteWinnerPolicy() {
    }

    public static String selectWinner(
            List<String> candidates,
            Map<String, Integer> voteCounts,
            RandomGenerator random
    ) {
        Objects.requireNonNull(random, "random");
        List<String> normalizedCandidates = MapVoteCandidatePolicy.normalizePool(candidates);
        if (normalizedCandidates.isEmpty()) return "";

        int best = 0;
        for (String candidate : normalizedCandidates) {
            best = Math.max(best, Math.max(0, voteCounts == null ? 0 : voteCounts.getOrDefault(candidate, 0)));
        }

        List<String> leaders = new ArrayList<>();
        for (String candidate : normalizedCandidates) {
            int votes = Math.max(0, voteCounts == null ? 0 : voteCounts.getOrDefault(candidate, 0));
            if (votes == best) leaders.add(candidate);
        }
        return leaders.get(random.nextInt(leaders.size()));
    }
}
