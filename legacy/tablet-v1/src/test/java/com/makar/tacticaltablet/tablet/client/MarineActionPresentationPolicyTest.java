package com.makar.tacticaltablet.tablet.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarineActionPresentationPolicyTest {
    @Test
    void playerOutsideClanCannotUseMarine() {
        var result = describe(false, false, false, false, false, "", true);

        assertEquals("Требуется клан", result.status());
        assertFalse(result.active());
        assertEquals(MarineActionPresentationPolicy.UNAVAILABLE_COLOR, result.color());
    }

    @Test
    void lockedClanCannotUseMarine() {
        var result = describe(true, false, false, false, false, "", true);

        assertEquals("Требуется разблокировка клана", result.status());
        assertFalse(result.active());
    }

    @Test
    void clanLeaderWithEnoughClanCoinsSeesPurchase() {
        var result = describe(true, false, true, true, false, "", true);

        assertEquals("Покупка • 1000 КК", result.status());
        assertEquals("Глава клана может купить Морпеха", result.hint());
        assertTrue(result.active());
        assertEquals(MarineActionPresentationPolicy.PURCHASE_COLOR, result.color());
    }

    @Test
    void clanUnlockMakesMarineGreenAndActiveWithoutPersonalPurchase() {
        var result = describe(true, true, false, true, false, "", true);

        assertEquals("Разблокирован кланом", result.status());
        assertEquals("Класс доступен", result.hint());
        assertTrue(result.active());
        assertEquals(MarineActionPresentationPolicy.AVAILABLE_COLOR, result.color());
        assertEquals("✓", result.marker());
    }

    @Test
    void usedKitTakesPrecedenceOverUnlockedPresentation() {
        var result = describe(true, true, false, false, true, "", true);

        assertEquals("Уже использован", result.status());
        assertFalse(result.active());
    }

    @Test
    void cooldownTakesPrecedenceOverExclusiveClassPresentation() {
        var result = describe(true, true, false, false, false, "00:12", true);

        assertEquals(TabletStatusFormatter.cooldown("00:12"), result.status());
        assertEquals("Класс на перезарядке", result.hint());
        assertFalse(result.active());
    }

    private static MarineActionPresentationPolicy.Presentation describe(
            boolean inClan,
            boolean clanUnlocked,
            boolean canBuy,
            boolean actionActive,
            boolean kitUsed,
            String cooldown,
            boolean gameRunning
    ) {
        return MarineActionPresentationPolicy.describe(
                inClan,
                clanUnlocked,
                canBuy,
                actionActive,
                kitUsed,
                cooldown,
                gameRunning,
                1000
        );
    }
}
