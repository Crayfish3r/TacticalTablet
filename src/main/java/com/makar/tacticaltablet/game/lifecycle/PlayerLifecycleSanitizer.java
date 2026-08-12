package com.makar.tacticaltablet.game.lifecycle;

import com.makar.tacticaltablet.integration.curios.CuriosInventoryBridge;
import net.minecraft.server.level.ServerPlayer;

/** Server-thread lifecycle boundaries for state that must not leak between lives. */
public final class PlayerLifecycleSanitizer {
    private PlayerLifecycleSanitizer() {
    }

    /** Removes inventory-backed and runtime state belonging to the previous life. */
    public static void clearPreviousLifeState(ServerPlayer player) {
        if (player == null) {
            return;
        }

        CuriosInventoryBridge.clear(player);
        resetTransientState(player);
    }

    /** Normalizes combat state immediately before the player becomes an active PvP participant. */
    public static void prepareForDeployment(ServerPlayer player) {
        resetTransientState(player);
    }

    /** Repairs an illegally lethal lobby state without destroying a newly selected kit. */
    public static void restoreLobbySafety(ServerPlayer player) {
        resetTransientState(player);
    }

    private static void resetTransientState(ServerPlayer player) {
        if (player == null) {
            return;
        }

        player.removeAllEffects();
        player.setAbsorptionAmount(0.0F);
        player.clearFire();
        player.setTicksFrozen(0);
        player.fallDistance = 0.0F;
        player.setAirSupply(player.getMaxAirSupply());

        float maxHealth = player.getMaxHealth();
        if (Float.isFinite(maxHealth) && maxHealth > 0.0F) {
            player.setHealth(maxHealth);
        }
    }
}
