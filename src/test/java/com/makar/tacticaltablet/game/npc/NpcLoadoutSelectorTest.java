package com.makar.tacticaltablet.game.npc;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class NpcLoadoutSelectorTest {
    @Test void selectionNeverExceedsCapacityAndUsesOnlyCompatiblePresets() {
        var c = new NpcEncounterConfig(); var selector = new NpcLoadoutSelector(); var random = new Random(4);
        for (int capacity = 0; capacity <= 12; capacity++) {
            for (int i = 0; i < 200; i++) {
                var selected = selector.select(c, capacity, Set.of(), random);
                if (capacity == 0) { assertTrue(selected.isEmpty()); continue; }
                var s = selected.orElseThrow(); int size = s.weapons().size();
                assertTrue(size <= capacity && size <= c.maxGroupSize && size >= c.minGroupSize);
                assertTrue(size >= s.preset().minSize() && size <= s.preset().maxSize());
                assertEquals(s.preset().weapons().subList(0, size), s.weapons());
            }
        }
    }

    @Test void individualWeaponSlotsArePreservedAndInvalidGunPresetIsQuarantined() {
        var c = new NpcEncounterConfig(); c.minGroupSize = c.maxGroupSize = 2;
        var p = new NpcEncounterConfig.Preset("pack", 1, 2, 2,
                List.of("installed_pack:first", "installed_pack:second"), "PATROL", "FINITE", 42, 16);
        c.presets = List.of(p); var selector = new NpcLoadoutSelector();
        var selected = selector.select(c, 2, Set.of(), new Random()).orElseThrow();
        assertEquals(p.weapons(), selected.weapons());
        assertTrue(selector.select(c, 2, Set.of("pack"), new Random()).isEmpty());
        assertTrue(selector.select(c, 1, Set.of(), new Random()).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> selected.weapons().add("tacz:ak47"));
    }

    @Test void weightsAndSizesAreBothRandomized() {
        var c = new NpcEncounterConfig(); var ids = new java.util.HashSet<String>();
        var sizes = new java.util.HashSet<Integer>(); var selector = new NpcLoadoutSelector(); var r = new Random(5);
        for (int i = 0; i < 1000; i++) {
            var s = selector.select(c, 12, Set.of(), r).orElseThrow();
            ids.add(s.preset().id()); sizes.add(s.weapons().size());
        }
        assertEquals(4, ids.size()); assertEquals(Set.of(1, 2, 3, 4), sizes);
    }
}
