package com.makar.tacticaltablet.integration.discord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchDamageAccountingTest {
    @Test
    void recordsActualHealthLostFromHealthDelta() {
        assertEquals(5.0D, MatchDamageAccounting.actualHealthLost(20.0F, 15.0F));
    }

    @Test
    void absorptionOnlyDamageDoesNotCountAsHealthDamage() {
        assertEquals(0.0D, MatchDamageAccounting.actualHealthLost(20.0F, 20.0F));
        assertEquals(0.0D, MatchDamageAccounting.actualHealthLostFromIncomingDamage(20.0F, 4.0F, 3.0F));
    }

    @Test
    void partialAbsorptionCountsOnlyHealthDamage() {
        assertEquals(3.0D, MatchDamageAccounting.actualHealthLost(20.0F, 17.0F));
        assertEquals(3.0D, MatchDamageAccounting.actualHealthLostFromIncomingDamage(20.0F, 2.0F, 5.0F));
    }

    @Test
    void overkillIsClampedToRemainingHealth() {
        assertEquals(3.0D, MatchDamageAccounting.actualHealthLostFromFinalDamage(3.0F, 20.0F));
        assertEquals(3.0D, MatchDamageAccounting.actualHealthLost(3.0F, -17.0F));
    }

    @Test
    void canceledOrBlockedDamageIsNotRecorded() {
        assertFalse(MatchDamageAccounting.shouldRecordDamage(true, false, true, true, 5.0D));
        assertFalse(MatchDamageAccounting.shouldRecordDamage(false, false, true, true, 0.0D));
    }

    @Test
    void forbiddenFriendlyFireIsNotRecorded() {
        assertFalse(MatchDamageAccounting.shouldRecordDamage(false, true, true, true, 5.0D));
    }

    @Test
    void damageRequiresBothParticipants() {
        assertFalse(MatchDamageAccounting.shouldRecordDamage(false, false, false, true, 5.0D));
        assertFalse(MatchDamageAccounting.shouldRecordDamage(false, false, true, false, 5.0D));
        assertTrue(MatchDamageAccounting.shouldRecordDamage(false, false, true, true, 5.0D));
    }
}
