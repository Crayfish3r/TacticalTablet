package com.makar.tacticaltablet.casino;

import com.makar.tacticaltablet.progression.PlayerProgressManager;
import com.makar.tacticaltablet.progression.ShopClassCatalog;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CasinoSpinTableTest {

    @Test
    void onlyConfiguredStakeCycleIsAccepted() {
        assertEquals(java.util.List.of(50, 100, 250, 500, 1000), CasinoSpinTable.STAKES);
        for (int stake : CasinoSpinTable.STAKES) assertTrue(CasinoSpinTable.isAllowedStake(stake));
        assertFalse(CasinoSpinTable.isAllowedStake(0));
        assertFalse(CasinoSpinTable.isAllowedStake(49));
        assertFalse(CasinoSpinTable.isAllowedStake(1001));
    }

    @Test
    void seededRollsOnlyProduceOwnedRewardCatalogEntries() {
        CasinoSpinTable table = new CasinoSpinTable(new Random(0xD3117E));
        Set<String> shopClasses = ShopClassCatalog.entries().stream()
                .map(ShopClassCatalog.Entry::classKey)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> vipClasses = Set.of(PlayerProgressManager.getExclusiveClasses());
        EnumSet<CasinoRewardKind> observed = EnumSet.noneOf(CasinoRewardKind.class);

        for (int stake : CasinoSpinTable.STAKES) {
            for (int iteration = 0; iteration < 20_000; iteration++) {
                CasinoReward reward = table.roll(stake);
                observed.add(reward.kind());
                assertTrue(reward.coins() >= 0);
                assertTrue(reward.duplicateCompensation() >= 0);
                switch (reward.kind()) {
                    case COINS -> assertTrue(reward.classId().isEmpty());
                    case SHOP_CLASS -> {
                        assertTrue(shopClasses.contains(reward.classId()));
                        assertTrue(reward.duplicateCompensation() > 0);
                    }
                    case VIP_CLASS -> {
                        assertTrue(vipClasses.contains(reward.classId()));
                        assertTrue(reward.duplicateCompensation() > 0);
                    }
                    case SAD_TROMBONE -> {
                        assertTrue(reward.classId().isEmpty());
                        assertTrue(reward.duplicateCompensation() > 0);
                    }
                }
            }
        }

        assertEquals(EnumSet.allOf(CasinoRewardKind.class), observed);
    }

    @Test
    void unsupportedStakeCannotReachRewardRoll() {
        CasinoSpinTable table = new CasinoSpinTable(new Random(1L));
        assertThrows(IllegalArgumentException.class, () -> table.roll(75));
    }
}
