package com.makar.tacticaltablet.tablet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompetitiveClassPolicyTest {
    @Test
    void blocksOnlyVipClassesInCompetitiveSet() {
        assertFalse(CompetitiveClassPolicy.isAllowed(true, ClassCategory.EXCLUSIVE));
        assertTrue(CompetitiveClassPolicy.isAllowed(true, ClassCategory.BASE));
        assertTrue(CompetitiveClassPolicy.isAllowed(false, ClassCategory.EXCLUSIVE));
        assertTrue(CompetitiveClassPolicy.isVipBlocked(true, "medic"));
        assertFalse(CompetitiveClassPolicy.isVipBlocked(true, "sniper"));
    }
}
