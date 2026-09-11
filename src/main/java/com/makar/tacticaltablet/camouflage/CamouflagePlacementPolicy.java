package com.makar.tacticaltablet.camouflage;

/** Minecraft-free placement decision used by armor and Curios adapters. */
public final class CamouflagePlacementPolicy {
    private CamouflagePlacementPolicy() { }

    public static Decision decide(CamouflagePolicy policy, boolean targetOccupied,
                                  boolean targetIsAutoCamouflage, boolean inventoryHasSpace) {
        if (!targetOccupied) return Decision.EQUIP;
        if (policy == CamouflagePolicy.REPLACE_AUTO_ONLY && targetIsAutoCamouflage) return Decision.REPLACE;
        return inventoryHasSpace ? Decision.INVENTORY : Decision.SKIP;
    }

    public enum Decision { EQUIP, REPLACE, INVENTORY, SKIP }
}
