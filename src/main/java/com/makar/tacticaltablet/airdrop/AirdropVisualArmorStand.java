package com.makar.tacticaltablet.airdrop;

import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;

/**
 * Server-side carrier for the falling crate item model.
 * It must never be written to chunk storage: the AirDrop manager owns its lifetime.
 */
public final class AirdropVisualArmorStand extends ArmorStand {
    public AirdropVisualArmorStand(Level level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
