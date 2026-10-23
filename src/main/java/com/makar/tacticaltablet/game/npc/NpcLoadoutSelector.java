package com.makar.tacticaltablet.game.npc;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Select size first, then a weighted preset which can supply every individual weapon slot. */
public final class NpcLoadoutSelector {
    public record Selection(NpcEncounterConfig.Preset preset, List<String> weapons) {
        public Selection { weapons = List.copyOf(weapons); }
    }

    public Optional<Selection> select(NpcEncounterConfig c, int available, Set<String> rejected, RandomGenerator random) {
        int max = Math.min(c.maxGroupSize, available);
        if (max < c.minGroupSize) return Optional.empty();
        var sizes = java.util.stream.IntStream.rangeClosed(c.minGroupSize, max)
                .filter(size -> c.presets.stream().anyMatch(p -> eligible(p, size, rejected))).toArray();
        if (sizes.length == 0) return Optional.empty();
        int size = sizes[random.nextInt(sizes.length)];
        var candidates = c.presets.stream().filter(p -> eligible(p, size, rejected)).toList();
        int roll = random.nextInt(candidates.stream().mapToInt(NpcEncounterConfig.Preset::weight).sum());
        for (var p : candidates) {
            roll -= p.weight();
            if (roll < 0) return Optional.of(new Selection(p, p.weapons().subList(0, size)));
        }
        throw new IllegalStateException("Validated preset weights exhausted");
    }

    private static boolean eligible(NpcEncounterConfig.Preset p, int size, Set<String> rejected) {
        return p.weight() > 0 && !rejected.contains(p.id()) && size >= p.minSize() && size <= p.maxSize();
    }
}
