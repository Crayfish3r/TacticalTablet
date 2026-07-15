package com.makar.tacticaltablet.game.set;

import com.makar.tacticaltablet.clan.transaction.RepositoryResult;
import com.makar.tacticaltablet.progression.PlayerProgressManager;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SetRewardService {
    private SetRewardService() { }

    public static int calculateSetWinnerReward(int participantCount) {
        return participantCount < 2 ? 0 : 15 + Math.max(0, participantCount - 2) * 5;
    }

    public static int rewardedPlaces(int participantCount) {
        return participantCount < 2 ? 0 : participantCount > 5 ? 3 : 1;
    }

    public static List<PayoutResult> award(MinecraftServer server, SetRewardSummary summary) {
        if (server == null || summary == null || summary.rewardCoins() <= 0) return List.of();
        List<PayoutResult> results = new ArrayList<>();
        for (SetPlacement placement : summary.placements()) {
            String key = idempotencyKey(summary.setId(), placement);
            RepositoryResult result = PlayerProgressManager.applyIdempotentCoinCredit(
                    server, placement.playerId(), placement.playerName(), summary.rewardCoins(), key);
            results.add(new PayoutResult(placement, result));
        }
        return List.copyOf(results);
    }

    public static String idempotencyKey(java.util.UUID setId, SetPlacement placement) {
        if (setId == null || placement == null || placement.playerId() == null) {
            throw new IllegalArgumentException("Set reward identity is incomplete");
        }
        return "set:" + setId + ":place:" + placement.place() + ":player:" + placement.playerId();
    }

    public static Set<Integer> successfullyPersistedPlaces(List<PayoutResult> payouts) {
        if (payouts == null || payouts.isEmpty()) return Set.of();
        java.util.LinkedHashSet<Integer> places = new java.util.LinkedHashSet<>();
        for (PayoutResult payout : payouts) {
            if (payout != null && payout.placement() != null
                    && (payout.result().status() == RepositoryResult.Status.APPLIED
                    || payout.result().status() == RepositoryResult.Status.ALREADY_APPLIED)) {
                places.add(payout.placement().place());
            }
        }
        return Set.copyOf(places);
    }

    public record PayoutResult(SetPlacement placement, RepositoryResult result) { }
}
