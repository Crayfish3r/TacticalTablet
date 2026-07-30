package com.makar.tacticaltablet.game.balance;

import net.minecraft.resources.ResourceLocation;

final class Py132BalancePolicy {

    static final ResourceLocation LASER_HIT =
            ResourceLocation.fromNamespaceAndPath("ts", "laserhit");

    private Py132BalancePolicy() {
    }

    static boolean appliesTo(ResourceLocation damageType, boolean enabled) {
        return enabled && LASER_HIT.equals(damageType);
    }

    static float adjustDamage(
            ResourceLocation damageType,
            float amount,
            boolean enabled,
            float multiplier
    ) {
        return appliesTo(damageType, enabled) ? amount * multiplier : amount;
    }
}
