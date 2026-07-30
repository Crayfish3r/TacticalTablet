package com.makar.tacticaltablet.game.balance;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Py132BalancePolicyTest {

    @Test
    void scalesLaserDamageToConfiguredFraction() {
        assertEquals(7.0F, adjust("ts:laserhit", 20.0F, true, 0.35F));
        assertEquals(3.5F, adjust("ts:laserhit", 10.0F, true, 0.35F));
    }

    @Test
    void leavesOtherDamageTypesUnchanged() {
        assertEquals(20.0F, adjust("minecraft:generic", 20.0F, true, 0.35F));
    }

    @Test
    void leavesLaserDamageUnchangedWhenDisabled() {
        assertEquals(20.0F, adjust("ts:laserhit", 20.0F, false, 0.35F));
    }

    @Test
    void supportsMultiplierBounds() {
        assertEquals(20.0F, adjust("ts:laserhit", 20.0F, true, 1.0F));
        assertEquals(0.0F, adjust("ts:laserhit", 20.0F, true, 0.0F));
    }

    private static float adjust(String damageType, float amount, boolean enabled, float multiplier) {
        return Py132BalancePolicy.adjustDamage(
                ResourceLocation.parse(damageType),
                amount,
                enabled,
                multiplier
        );
    }
}
