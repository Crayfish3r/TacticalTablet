package com.makar.tacticaltablet.progression;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class TestMutableProgressState implements MutableProgressState {
    private int coins;
    private final Map<String, Integer> experience = new HashMap<>();
    private final Map<String, Integer> tiers = new HashMap<>();
    private final Map<String, Integer> baseUnlocks = new HashMap<>();
    private final Map<String, Integer> purchases = new HashMap<>();
    private final Map<Counter, Integer> counters = new EnumMap<>(Counter.class);
    private final Map<Flag, Boolean> flags = new EnumMap<>(Flag.class);
    private final List<ProgressReceipt> receipts = new ArrayList<>();

    TestMutableProgressState(int coins) {
        this.coins = coins;
    }

    @Override
    public int coins() {
        return coins;
    }

    @Override
    public void coins(int value) {
        coins = value;
    }

    @Override
    public ProgressEntry experience(String classId) {
        return ProgressEntry.from(experience.get(classId));
    }

    @Override
    public void experience(String classId, int value) {
        experience.put(classId, value);
    }

    @Override
    public void removeExperience(String classId) {
        experience.remove(classId);
    }

    @Override
    public ProgressEntry tier(String classId) {
        return ProgressEntry.from(tiers.get(classId));
    }

    @Override
    public void tier(String classId, int value) {
        tiers.put(classId, value);
    }

    @Override
    public ProgressEntry baseUnlock(String classId) {
        return ProgressEntry.from(baseUnlocks.get(classId));
    }

    @Override
    public void baseUnlock(String classId, int value) {
        baseUnlocks.put(classId, value);
    }

    @Override
    public void removeBaseUnlock(String classId) {
        baseUnlocks.remove(classId);
    }

    @Override
    public ProgressEntry purchase(String classId) {
        return ProgressEntry.from(purchases.get(classId));
    }

    @Override
    public void purchase(String classId, int value) {
        purchases.put(classId, value);
    }

    @Override
    public void removePurchase(String classId) {
        purchases.remove(classId);
    }

    @Override
    public int counter(Counter counter) {
        return counters.getOrDefault(counter, 0);
    }

    @Override
    public void counter(Counter counter, int value) {
        counters.put(counter, value);
    }

    @Override
    public boolean flag(Flag flag) {
        return flags.getOrDefault(flag, false);
    }

    @Override
    public void flag(Flag flag, boolean value) {
        flags.put(flag, value);
    }

    @Override
    public Optional<ProgressReceipt> receipt(String receiptId) {
        return receipts.stream().filter(receipt -> receipt.transactionId().equals(receiptId)).findFirst();
    }

    @Override
    public void addReceipt(ProgressReceipt receipt) {
        receipts.add(receipt);
    }

    @Override
    public boolean removeReceipt(ProgressReceipt receipt) {
        return receipts.remove(receipt);
    }

    int receiptCount() {
        return receipts.size();
    }
}
