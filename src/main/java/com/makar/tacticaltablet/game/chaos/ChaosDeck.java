package com.makar.tacticaltablet.game.chaos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class ChaosDeck {
    public static final int CLASSES_PER_GAME = 3;

    private final List<String> cards;

    public ChaosDeck(List<String> classIds, int games, RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        int required = Math.multiplyExact(Math.max(1, games), CLASSES_PER_GAME);
        List<String> variants = new ArrayList<>(new LinkedHashSet<>(classIds == null ? List.of() : classIds));
        variants.removeIf(id -> id == null || id.isBlank());
        int uniqueClasses = (int) variants.stream().map(ChaosClassCard::identity).distinct().count();
        if (uniqueClasses < required) {
            throw new IllegalArgumentException("Chaos requires " + required + " unique classes, but only " + uniqueClasses + " are registered");
        }
        for (int i = variants.size() - 1; i > 0; i--) {
            int swap = random.nextInt(i + 1);
            String value = variants.get(i);
            variants.set(i, variants.get(swap));
            variants.set(swap, value);
        }
        List<String> selected = new ArrayList<>();
        LinkedHashSet<String> selectedClasses = new LinkedHashSet<>();
        for (String variant : variants) {
            if (!selectedClasses.add(ChaosClassCard.identity(variant))) continue;
            selected.add(variant);
            if (selected.size() == required) break;
        }
        cards = List.copyOf(selected);
    }

    private ChaosDeck(List<String> cards) { this.cards = List.copyOf(cards); }

    public static ChaosDeck restore(List<String> cards, int games) {
        int required = Math.multiplyExact(Math.max(1, games), CLASSES_PER_GAME);
        List<String> safe = cards == null ? List.of() : List.copyOf(cards);
        if (safe.size() != required || new LinkedHashSet<>(safe).size() != required
                || safe.stream().map(ChaosClassCard::identity).distinct().count() != required
                || safe.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("Invalid persisted Chaos deck");
        }
        return new ChaosDeck(safe);
    }

    public List<String> gameClasses(int oneBasedGame) {
        int offset = (oneBasedGame - 1) * CLASSES_PER_GAME;
        if (oneBasedGame < 1 || offset + CLASSES_PER_GAME > cards.size()) return List.of();
        return cards.subList(offset, offset + CLASSES_PER_GAME);
    }

    public List<String> cards() { return cards; }
}
