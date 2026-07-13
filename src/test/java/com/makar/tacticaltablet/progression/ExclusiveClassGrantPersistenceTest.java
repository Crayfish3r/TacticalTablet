package com.makar.tacticaltablet.progression;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExclusiveClassGrantPersistenceTest {

    @Test
    void successfulGrantAddsPurchaseAndXpEntryBeforePersisting() {
        Map<String, Integer> purchased = new HashMap<>();
        Map<String, Integer> classes = new HashMap<>();

        PlayerProgressManager.ExclusiveClassGrantResult result = PlayerProgressManager.grantExclusiveClassForPersistence(
                purchased, classes, "medic", () -> true
        );

        assertEquals(PlayerProgressManager.ExclusiveClassGrantResult.GRANTED, result);
        assertEquals(1, purchased.get("medic"));
        assertEquals(0, classes.get("medic"));
    }

    @Test
    void alreadyOwnedClassDoesNotPersistOrChangeData() {
        Map<String, Integer> purchased = new HashMap<>(Map.of("medic", 1));
        Map<String, Integer> classes = new HashMap<>(Map.of("medic", 25));

        PlayerProgressManager.ExclusiveClassGrantResult result = PlayerProgressManager.grantExclusiveClassForPersistence(
                purchased, classes, "medic", () -> {
                    throw new AssertionError("already-owned class must not be saved again");
                }
        );

        assertEquals(PlayerProgressManager.ExclusiveClassGrantResult.ALREADY_OWNED, result);
        assertEquals(1, purchased.get("medic"));
        assertEquals(25, classes.get("medic"));
    }

    @Test
    void invalidClassDoesNotChangeProfile() {
        Map<String, Integer> purchased = new HashMap<>();
        Map<String, Integer> classes = new HashMap<>();

        PlayerProgressManager.ExclusiveClassGrantResult result = PlayerProgressManager.grantExclusiveClassForPersistence(
                purchased, classes, "unknown", () -> true
        );

        assertEquals(PlayerProgressManager.ExclusiveClassGrantResult.INVALID_CLASS, result);
        assertTrue(purchased.isEmpty());
        assertTrue(classes.isEmpty());
    }

    @Test
    void failedPersistenceRestoresPurchasedAndXpMaps() {
        Map<String, Integer> purchased = new HashMap<>();
        Map<String, Integer> classes = new HashMap<>();

        PlayerProgressManager.ExclusiveClassGrantResult result = PlayerProgressManager.grantExclusiveClassForPersistence(
                purchased, classes, "railgunner", () -> false
        );

        assertEquals(PlayerProgressManager.ExclusiveClassGrantResult.SAVE_FAILED, result);
        assertFalse(purchased.containsKey("railgunner"));
        assertFalse(classes.containsKey("railgunner"));
    }

    @Test
    void failedPersistenceRestoresPreexistingXpValue() {
        Map<String, Integer> purchased = new HashMap<>();
        Map<String, Integer> classes = new HashMap<>(Map.of("killer", 42));

        PlayerProgressManager.ExclusiveClassGrantResult result = PlayerProgressManager.grantExclusiveClassForPersistence(
                purchased, classes, "killer", () -> false
        );

        assertEquals(PlayerProgressManager.ExclusiveClassGrantResult.SAVE_FAILED, result);
        assertFalse(purchased.containsKey("killer"));
        assertEquals(42, classes.get("killer"));
    }
}
