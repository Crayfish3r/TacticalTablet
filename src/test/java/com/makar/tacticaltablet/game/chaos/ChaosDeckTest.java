package com.makar.tacticaltablet.game.chaos;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChaosDeckTest {
    @Test
    void usesFifteenUniqueClassesAcrossFiveGames() {
        List<String> pool = new ArrayList<>();
        for (int i = 0; i < 23; i++) pool.add("class_" + i);
        ChaosDeck deck = new ChaosDeck(pool, 5, new Random(42));

        assertEquals(15, deck.cards().size());
        assertEquals(15, new HashSet<>(deck.cards()).size());
        for (int game = 1; game <= 5; game++) {
            assertEquals(3, deck.gameClasses(game).size());
            assertEquals(3, new HashSet<>(deck.gameClasses(game)).size());
        }
    }

    @Test
    void rejectsAnIncompletePoolClearly() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChaosDeck(List.of("a", "b", "c"), 5, new Random(1)));
    }

    @Test
    void tierVariantsNeverRepeatTheUnderlyingClass() {
        List<String> pool = new ArrayList<>();
        for (int classIndex = 0; classIndex < 18; classIndex++) {
            for (int tier = 0; tier < 5; tier++) pool.add("class_" + classIndex + "@" + tier);
        }
        ChaosDeck deck = new ChaosDeck(pool, 5, new Random(7));

        assertEquals(15, deck.cards().stream().map(ChaosClassCard::decode)
                .map(ChaosClassCard::classId).distinct().count());
        assertEquals(15, deck.cards().stream().map(ChaosClassCard::decode)
                .map(ChaosClassCard::tier).filter(tier -> tier >= 0 && tier <= 4).count());
    }

    @Test
    void authoritativeRegistryProvidesEnoughClassesAndEveryBaseTier() {
        List<String> pool = ChaosSetManager.buildClassPool();

        assertTrue(pool.stream().map(ChaosClassCard::identity).distinct().count() >= 15);
        for (int tier = 0; tier <= 4; tier++) {
            int expectedTier = tier;
            assertTrue(pool.contains("sniper@" + expectedTier));
        }
    }
}
