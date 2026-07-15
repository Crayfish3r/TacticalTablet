package com.makar.tacticaltablet.game.set;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

public record SetLeaderboardSnapshot(UUID setId, int participantCount, List<SetPlayerResult> orderedResults) {
    public SetLeaderboardSnapshot {
        ArrayList<SetPlayerResult> sorted = new ArrayList<>(orderedResults == null ? List.of() : orderedResults);
        sorted.removeIf(java.util.Objects::isNull);
        sorted.sort(SetScoringRules.SET_RESULT_COMPARATOR);
        orderedResults = List.copyOf(sorted);
        participantCount = Math.max(0, participantCount);
    }
}
