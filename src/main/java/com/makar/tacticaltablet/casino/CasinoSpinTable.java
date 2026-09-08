package com.makar.tacticaltablet.casino;

import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.progression.ShopClassCatalog;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Server-authoritative, data-only reward table. Intermediate animation never participates in the roll. */
public final class CasinoSpinTable {
    public static final List<Integer> STAKES = List.of(50, 100, 250, 500, 1000);

    private static final List<String> SHOP_CLASSES = ShopClassCatalog.entries().stream()
            .map(ShopClassCatalog.Entry::classKey)
            .toList();
    private static final List<String> VIP_CLASSES = List.of(PlayerProgressManager.getExclusiveClasses());
    private static final Map<Integer, List<WeightedReward>> TABLES = Map.of(
            50, table(60, 0, 24, 25, 10, 75, 5, 150, 0, 0, 1),
            100, table(48, 0, 25, 50, 15, 150, 8, 300, 3, 0, 1),
            250, table(40, 0, 22, 100, 18, 375, 10, 750, 8, 1, 1),
            500, table(35, 0, 25, 250, 20, 750, 10, 1500, 7, 2, 1),
            1000, table(30, 0, 25, 500, 20, 1500, 12, 3000, 9, 3, 1)
    );
    private static final List<OddsRow> ODDS_ROWS = STAKES.stream()
            .map(CasinoSpinTable::oddsFor)
            .toList();

    private final RandomGenerator random;

    public CasinoSpinTable() {
        this(new SecureRandom());
    }

    CasinoSpinTable(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public CasinoReward roll(int stake) {
        List<WeightedReward> table = TABLES.get(stake);
        if (table == null) throw new IllegalArgumentException("Unsupported casino stake: " + stake);
        int totalWeight = table.stream().mapToInt(WeightedReward::weight).sum();
        int roll = random.nextInt(totalWeight);
        for (WeightedReward entry : table) {
            roll -= entry.weight();
            if (roll < 0) return materialize(entry.reward(), stake);
        }
        throw new IllegalStateException("Casino reward table has no terminal entry");
    }

    public static boolean isAllowedStake(int stake) {
        return STAKES.contains(stake);
    }

    /** Immutable presentation snapshot derived from the authoritative weighted tables. */
    public static List<OddsRow> oddsRows() {
        return ODDS_ROWS;
    }

    private static OddsRow oddsFor(int stake) {
        List<WeightedReward> table = TABLES.get(stake);
        int[] coinWeights = new int[4];
        int[] coinRewards = new int[4];
        int coinIndex = 0;
        int shopWeight = 0;
        int vipWeight = 0;
        int sadWeight = 0;
        for (WeightedReward entry : table) {
            switch (entry.reward().kind()) {
                case COINS -> {
                    if (coinIndex >= coinWeights.length) {
                        throw new IllegalStateException("Casino table has too many coin tiers for stake " + stake);
                    }
                    coinWeights[coinIndex] = entry.weight();
                    coinRewards[coinIndex] = entry.reward().coins();
                    coinIndex++;
                }
                case SHOP_CLASS -> shopWeight += entry.weight();
                case VIP_CLASS -> vipWeight += entry.weight();
                case SAD_TROMBONE -> sadWeight += entry.weight();
            }
        }
        if (coinIndex != coinWeights.length) {
            throw new IllegalStateException("Casino table must expose four coin tiers for stake " + stake);
        }
        return new OddsRow(
                stake,
                coinWeights[0],
                coinWeights[1], coinRewards[1],
                coinWeights[2], coinRewards[2],
                coinWeights[3], coinRewards[3],
                shopWeight,
                vipWeight,
                sadWeight
        );
    }

    private CasinoReward materialize(CasinoReward reward, int stake) {
        if (reward.kind() == CasinoRewardKind.SHOP_CLASS) {
            String classId = SHOP_CLASSES.get(random.nextInt(SHOP_CLASSES.size()));
            int price = ShopClassCatalog.byClassKey(classId).map(ShopClassCatalog.Entry::price).orElse(stake);
            return CasinoReward.shopClass(classId, Math.max(25, price * 3 / 5));
        }
        if (reward.kind() == CasinoRewardKind.VIP_CLASS) {
            return CasinoReward.vipClass(VIP_CLASSES.get(random.nextInt(VIP_CLASSES.size())), 750);
        }
        if (reward.kind() == CasinoRewardKind.SAD_TROMBONE) {
            return CasinoReward.sadTrombone(75);
        }
        return reward;
    }

    private static List<WeightedReward> table(
            int lossWeight, int lossCoins,
            int smallWeight, int smallCoins,
            int mediumWeight, int mediumCoins,
            int largeWeight, int largeCoins,
            int shopWeight, int vipWeight, int sadWeight
    ) {
        List<WeightedReward> rewards = new ArrayList<>();
        add(rewards, lossWeight, CasinoReward.coins(lossCoins));
        add(rewards, smallWeight, CasinoReward.coins(smallCoins));
        add(rewards, mediumWeight, CasinoReward.coins(mediumCoins));
        add(rewards, largeWeight, CasinoReward.coins(largeCoins));
        add(rewards, shopWeight, CasinoReward.shopClass("", 0));
        add(rewards, vipWeight, CasinoReward.vipClass("", 0));
        add(rewards, sadWeight, CasinoReward.sadTrombone(0));
        return List.copyOf(rewards);
    }

    private static void add(List<WeightedReward> rewards, int weight, CasinoReward reward) {
        if (weight > 0) rewards.add(new WeightedReward(weight, reward));
    }

    public record OddsRow(
            int stake,
            int noPrizeChance,
            int smallChance,
            int smallCoins,
            int mediumChance,
            int mediumCoins,
            int largeChance,
            int largeCoins,
            int shopClassChance,
            int vipClassChance,
            int sadTromboneChance
    ) {
        public OddsRow {
            int total = noPrizeChance + smallChance + mediumChance + largeChance
                    + shopClassChance + vipClassChance + sadTromboneChance;
            if (total != 100) throw new IllegalArgumentException("Casino odds must total 100, got " + total);
        }
    }

    private record WeightedReward(int weight, CasinoReward reward) {
        private WeightedReward {
            if (weight <= 0) throw new IllegalArgumentException("Weight must be positive");
        }
    }
}
