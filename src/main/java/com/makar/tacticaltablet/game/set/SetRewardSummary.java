package com.makar.tacticaltablet.game.set;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SetRewardSummary(
        UUID setId,
        int participantCount,
        int rewardCoins,
        List<SetPlacement> placements,
        int payoutPolicyVersion,
        Map<Integer, Integer> coinsByPlace
) {
    public static final int LEGACY_EQUAL_PAYOUT_VERSION = 0;
    public static final int PER_PLACE_PAYOUT_VERSION = 1;

    public SetRewardSummary {
        placements = placements == null ? List.of() : List.copyOf(placements);
        if (payoutPolicyVersion < LEGACY_EQUAL_PAYOUT_VERSION) {
            throw new IllegalArgumentException("Payout policy version cannot be negative");
        }
        LinkedHashMap<Integer, Integer> normalizedPayouts = new LinkedHashMap<>();
        if (payoutPolicyVersion >= PER_PLACE_PAYOUT_VERSION && coinsByPlace != null) {
            coinsByPlace.forEach((place, coins) -> {
                if (place == null || place < 1 || coins == null || coins < 0) {
                    throw new IllegalArgumentException("Per-place payouts must use positive places and non-negative coins");
                }
            });
            coinsByPlace.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> normalizedPayouts.put(entry.getKey(), entry.getValue()));
        }
        coinsByPlace = Map.copyOf(normalizedPayouts);
    }

    /** Source-compatible constructor and recovery policy for summaries saved before per-place payouts existed. */
    public SetRewardSummary(UUID setId, int participantCount, int rewardCoins, List<SetPlacement> placements) {
        this(setId, participantCount, rewardCoins, placements, LEGACY_EQUAL_PAYOUT_VERSION, Map.of());
    }

    public static SetRewardSummary withPerPlacePayouts(
            UUID setId,
            int participantCount,
            int baseRewardCoins,
            List<SetPlacement> placements,
            Map<Integer, Integer> coinsByPlace
    ) {
        return new SetRewardSummary(setId, participantCount, baseRewardCoins, placements,
                PER_PLACE_PAYOUT_VERSION, coinsByPlace);
    }

    public boolean usesLegacyEqualPayouts() {
        return payoutPolicyVersion < PER_PLACE_PAYOUT_VERSION;
    }

    public int coinsForPlace(int place) {
        return usesLegacyEqualPayouts() ? rewardCoins : coinsByPlace.getOrDefault(place, 0);
    }

    public boolean hasPositivePayout() {
        return placements.stream().anyMatch(placement -> coinsForPlace(placement.place()) > 0);
    }
}
