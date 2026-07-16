package com.makar.tacticaltablet.game.set;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure policy for turning the existing casual-set reward into fixed per-place payouts. */
public final class SetRewardDistribution {
    private static final int[] PODIUM_WEIGHTS = {55, 30, 15};
    private static final int WEIGHT_TOTAL = 100;

    private SetRewardDistribution() { }

    public static Map<Integer, Integer> distribute(int baseReward, int rewardedPlaces) {
        if (baseReward < 0) throw new IllegalArgumentException("Base reward cannot be negative");
        if (rewardedPlaces == 0) return Map.of();
        if (rewardedPlaces == 1) return Map.of(1, baseReward);
        if (rewardedPlaces != PODIUM_WEIGHTS.length) {
            throw new IllegalArgumentException("Only zero, one, or three rewarded places are supported");
        }

        long totalPool = Math.multiplyExact((long) baseReward, rewardedPlaces);
        long[] payouts = new long[rewardedPlaces];
        List<Remainder> remainders = new ArrayList<>(rewardedPlaces);
        long assigned = 0L;
        for (int i = 0; i < rewardedPlaces; i++) {
            long weightedPool = Math.multiplyExact(totalPool, PODIUM_WEIGHTS[i]);
            payouts[i] = weightedPool / WEIGHT_TOTAL;
            assigned += payouts[i];
            remainders.add(new Remainder(i, weightedPool % WEIGHT_TOTAL));
        }

        remainders.sort(Comparator.comparingLong(Remainder::value).reversed()
                .thenComparingInt(Remainder::index));
        long remaining = totalPool - assigned;
        for (int i = 0; i < remaining; i++) {
            payouts[remainders.get(i).index()]++;
        }

        LinkedHashMap<Integer, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < payouts.length; i++) {
            result.put(i + 1, Math.toIntExact(payouts[i]));
        }
        return Collections.unmodifiableMap(result);
    }

    private record Remainder(int index, long value) { }
}
