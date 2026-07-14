package com.makar.tacticaltablet.progression.kit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KitTierMappingTest {

    @Test
    void mapsEveryTierToItsExpectedKitFileName() {
        assertEquals(List.of("class"), KitManager.getKitFileCandidates("class", 0, false));
        assertEquals(List.of("class_rare", "class"), KitManager.getKitFileCandidates("class", 1, false));
        assertEquals(List.of("class_epic", "class_rare", "class"), KitManager.getKitFileCandidates("class", 2, false));
        assertEquals(List.of("class_legend", "class_epic", "class_rare", "class"), KitManager.getKitFileCandidates("class", 3, false));
        assertEquals(List.of("class_monster", "class_legend", "class_epic", "class_rare", "class"),
                KitManager.getKitFileCandidates("class", 4, false));
    }

    @Test
    void altCandidatesUseTheExistingAltSuffixAtEveryFallbackStep() {
        assertEquals(List.of("class_monster_alt", "class_legend_alt", "class_epic_alt", "class_rare_alt", "class_alt"),
                KitManager.getKitFileCandidates("class", 4, true));
        assertEquals(List.of("class_rare_alt", "class_alt"), KitManager.getKitFileCandidates("class", 1, true));
    }
}
