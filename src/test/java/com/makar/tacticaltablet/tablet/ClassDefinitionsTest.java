package com.makar.tacticaltablet.tablet;

import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.progression.ClassTier;
import com.makar.tacticaltablet.progression.ShopClassCatalog;
import com.makar.tacticaltablet.tablet.client.ClassIconResolver;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassDefinitionsTest {
    @Test
    void preservesExistingActionIdsAndClassKeys() {
        Map<Integer, String> expected = new LinkedHashMap<>();
        expected.put(0, "stormtrooper");
        expected.put(1, "sniper");
        expected.put(2, "scout");
        expected.put(3, "droneoperator");
        expected.put(4, "boomguy");
        expected.put(5, "mortarman");
        expected.put(6, "dream");
        expected.put(8, "machinegunner");
        expected.put(9, "rpgtrooper");
        expected.put(10, "tagilla");
        expected.put(11, "blackops");
        expected.put(12, "cowboy");
        expected.put(13, "solider");
        expected.put(14, "rebel");
        expected.put(15, "saboteur");
        expected.put(16, "killer");
        expected.put(17, "miniboss");
        expected.put(18, "shahed");
        expected.put(19, "krot");
        expected.put(20, "marine");
        expected.put(21, "medic");
        expected.put(22, "microwave");
        expected.put(23, "railgunner");
        expected.put(24, "crossbowman");
        expected.put(25, "smartstormtrooper");

        assertEquals(expected, ClassDefinitions.actionIdToClassKey());
    }

    @Test
    void categoryResultsAreSortedByDisplayOrder() {
        List<Integer> orders = ClassDefinitions.byCategory(ClassCategory.SHOP).stream()
                .map(ClassDefinition::displayOrder)
                .toList();

        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), orders);
        assertEquals("Оператор дрона", ClassDefinitions.byClassKey("droneoperator").orElseThrow().name().getString());
        assertEquals("Рэйл-ганнер", ClassDefinitions.byClassKey("railgunner").orElseThrow().name().getString());
    }

    @Test
    void missingClassIconUsesFallback() {
        ClassDefinition definition = ClassDefinitions.byClassKey("stormtrooper").orElseThrow();

        assertEquals(ClassDefinitions.FALLBACK_ICON, ClassIconResolver.resolve(definition, ignored -> false));
        assertSame(definition.icon(), ClassIconResolver.resolve(definition, ignored -> true));
    }

    @Test
    void adapterMatchesAuthoritativeProgressionCategoriesAndPrices() {
        for (ClassDefinition definition : ClassDefinitions.all()) {
            switch (definition.category()) {
                case BASE -> assertTrue(PlayerProgressManager.isBaseProgressionClass(definition.classKey()));
                case SHOP -> {
                    assertTrue(PlayerProgressManager.isShopClass(definition.classKey()));
                    assertEquals(PlayerProgressManager.getShopPrice(definition.classKey()), definition.price());
                    assertEquals(PlayerProgressManager.getShopFixedLevel(definition.classKey()), definition.fixedTier());
                }
                case EXCLUSIVE -> {
                    if (!"marine".equals(definition.classKey())) {
                        assertTrue(PlayerProgressManager.isExclusiveClass(definition.classKey()));
                    }
                }
            }
        }
    }

    @Test
    void shopAndExclusiveDefinitionsMatchTheRequiredBalanceAndCategories() {
        Map<String, Integer> prices = new LinkedHashMap<>();
        Map<String, ClassTier> tiers = new LinkedHashMap<>();
        for (ShopClassCatalog.Entry entry : ShopClassCatalog.entries()) {
            prices.put(entry.classKey(), entry.price());
            tiers.put(entry.classKey(), entry.tier());
        }

        assertEquals(Map.ofEntries(
                Map.entry("solider", 50), Map.entry("blackops", 250), Map.entry("rebel", 500),
                Map.entry("saboteur", 250), Map.entry("dream", 50), Map.entry("shahed", 500),
                Map.entry("miniboss", 250), Map.entry("cowboy", 50), Map.entry("boomguy", 500),
                Map.entry("tagilla", 250), Map.entry("killer", 1000),
                Map.entry("crossbowman", 500)), prices);
        assertEquals(Map.ofEntries(
                Map.entry("solider", ClassTier.RARE), Map.entry("blackops", ClassTier.EPIC),
                Map.entry("rebel", ClassTier.LEGEND), Map.entry("saboteur", ClassTier.EPIC),
                Map.entry("dream", ClassTier.RARE), Map.entry("shahed", ClassTier.LEGEND),
                Map.entry("miniboss", ClassTier.EPIC), Map.entry("cowboy", ClassTier.RARE),
                Map.entry("boomguy", ClassTier.LEGEND), Map.entry("tagilla", ClassTier.EPIC),
                Map.entry("killer", ClassTier.MONSTER), Map.entry("crossbowman", ClassTier.LEGEND)), tiers);

        for (String classKey : prices.keySet()) {
            assertEquals(ClassCategory.SHOP, ClassDefinitions.byClassKey(classKey).orElseThrow().category());
        }
        assertEquals(ClassTier.LEGEND.id(), fixedTier("krot"));
        assertEquals(ClassTier.LEGEND.id(), fixedTier("medic"));
        assertEquals(ClassTier.LEGEND.id(), fixedTier("microwave"));
        assertEquals(ClassTier.MONSTER.id(), fixedTier("railgunner"));
        assertEquals(ClassTier.LEGEND.id(), fixedTier("marine"));
        assertEquals(ClassTier.MONSTER.id(), fixedTier("smartstormtrooper"));
        assertEquals("Smart-Штурмовик",
                ClassDefinitions.byClassKey("smartstormtrooper").orElseThrow().name().getString());
        assertEquals(List.of("krot", "medic", "microwave", "railgunner", "marine", "smartstormtrooper"),
                ClassDefinitions.byCategory(ClassCategory.EXCLUSIVE).stream()
                        .map(ClassDefinition::classKey)
                        .toList());
    }

    private static int fixedTier(String classKey) {
        return ClassDefinitions.byClassKey(classKey).orElseThrow().fixedTier();
    }
}
