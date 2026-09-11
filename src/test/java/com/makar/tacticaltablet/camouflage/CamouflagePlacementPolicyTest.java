package com.makar.tacticaltablet.camouflage;

import org.junit.jupiter.api.Test;

import static com.makar.tacticaltablet.camouflage.CamouflagePlacementPolicy.Decision.*;
import static org.junit.jupiter.api.Assertions.*;

class CamouflagePlacementPolicyTest {
    @Test
    void freeTargetEquipsWithoutInventoryCopy() {
        assertEquals(EQUIP, CamouflagePlacementPolicy.decide(
                CamouflagePolicy.EMPTY_ONLY, false, false, false));
    }

    @Test
    void occupiedClassEquipmentIsNeverReplaced() {
        assertEquals(INVENTORY, CamouflagePlacementPolicy.decide(
                CamouflagePolicy.EMPTY_ONLY, true, false, true));
        assertEquals(INVENTORY, CamouflagePlacementPolicy.decide(
                CamouflagePolicy.REPLACE_AUTO_ONLY, true, false, true));
    }

    @Test
    void onlyMarkedAutoCamouflageMayBeRefreshed() {
        assertEquals(REPLACE, CamouflagePlacementPolicy.decide(
                CamouflagePolicy.REPLACE_AUTO_ONLY, true, true, true));
        assertNotEquals(REPLACE, CamouflagePlacementPolicy.decide(
                CamouflagePolicy.EMPTY_ONLY, true, true, true));
    }

    @Test
    void fullInventorySkipsWithoutReplacingOrDropping() {
        assertEquals(SKIP, CamouflagePlacementPolicy.decide(
                CamouflagePolicy.EMPTY_ONLY, true, false, false));
    }

    @Test
    void nonOwnerIsNotEligibleForDelivery() {
        assertFalse(CamouflageDeploymentEligibility.canDeliver(true, false, true, true));
        assertTrue(CamouflageDeploymentEligibility.canDeliver(true, true, true, true));
    }

    @Test
    void missingOrUnknownMapPresetIsNotEligibleForDelivery() {
        assertFalse(CamouflageDeploymentEligibility.canDeliver(true, true, false, false));
        assertFalse(CamouflageDeploymentEligibility.canDeliver(true, true, true, false));
    }
}
