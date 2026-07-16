package com.makar.tacticaltablet.progression;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Pure progression calculations. This class does not own or mutate player state. */
public final class ProgressPolicy {

    private ProgressPolicy() {
    }

    public static int normalizeCoins(int coins) {
        return Math.max(0, coins);
    }

    public static int normalizeExperience(int experience, int maximumExperience) {
        return Math.max(0, Math.min(Math.max(0, maximumExperience), experience));
    }

    public static int saturatingAdd(int current, int amount) {
        if (amount > 0 && current > Integer.MAX_VALUE - amount) {
            return Integer.MAX_VALUE;
        }
        if (amount < 0 && current < Integer.MIN_VALUE - amount) {
            return Integer.MIN_VALUE;
        }
        return current + amount;
    }

    public static ProgressMutationResult changeCoins(int currentBalance, int amount) {
        int normalizedCurrent = normalizeCoins(currentBalance);
        int updated = normalizeCoins(saturatingAdd(normalizedCurrent, amount));
        return new ProgressMutationResult(updated != normalizedCurrent, normalizedCurrent, updated);
    }

    public static boolean canAfford(int balance, int price) {
        return price >= 0 && normalizeCoins(balance) >= price;
    }

    public static int calculateLevel(int experience, ProgressionRules rules) {
        int normalizedExperience = normalizeExperience(experience, rules.maximumExperience());
        int level = 0;
        for (int candidate = 0; candidate <= rules.maximumLevel(); candidate++) {
            if (normalizedExperience >= rules.experienceThresholds().get(candidate)) {
                level = candidate;
            }
        }
        return level;
    }

    public static ProgressPurchaseResult evaluatePurchase(
            int balance,
            int price,
            boolean alreadyOwned,
            boolean validItem
    ) {
        int normalizedBalance = normalizeCoins(balance);
        if (!validItem || price <= 0) {
            return rejected(ProgressPurchaseResult.Failure.INVALID_ITEM, normalizedBalance);
        }
        if (alreadyOwned) {
            return rejected(ProgressPurchaseResult.Failure.ALREADY_OWNED, normalizedBalance);
        }
        if (!canAfford(normalizedBalance, price)) {
            return rejected(ProgressPurchaseResult.Failure.INSUFFICIENT_FUNDS, normalizedBalance);
        }
        return new ProgressPurchaseResult(true, ProgressPurchaseResult.Failure.NONE,
                normalizedBalance, normalizedBalance - price);
    }

    static Map<String, Integer> normalizeNonNegativeValues(Map<String, Integer> input) {
        Map<String, Integer> result = new HashMap<>();
        if (input == null) return result;

        for (Map.Entry<String, Integer> entry : input.entrySet()) {
            String key = normalizeIdentifier(entry.getKey());
            if (key.isBlank()) continue;
            result.put(key, normalizeCoins(entry.getValue() == null ? 0 : entry.getValue()));
        }
        return result;
    }

    private static ProgressPurchaseResult rejected(ProgressPurchaseResult.Failure failure, int balance) {
        return new ProgressPurchaseResult(false, failure, balance, balance);
    }

    private static String normalizeIdentifier(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
