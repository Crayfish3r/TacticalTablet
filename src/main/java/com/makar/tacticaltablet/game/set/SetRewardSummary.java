package com.makar.tacticaltablet.game.set;

import java.util.List;
import java.util.UUID;

public record SetRewardSummary(UUID setId, int participantCount, int rewardCoins, List<SetPlacement> placements) {
    public SetRewardSummary {
        placements = placements == null ? List.of() : List.copyOf(placements);
    }
}
