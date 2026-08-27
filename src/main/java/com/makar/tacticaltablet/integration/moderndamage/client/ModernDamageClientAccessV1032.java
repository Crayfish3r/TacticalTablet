package com.makar.tacticaltablet.integration.moderndamage.client;

import com.makar.tacticaltablet.core.TacticalTabletClientConfig;
import com.moderndamage.control.attribute.ModAttributes;
import com.moderndamage.control.client.ClientArmStaminaCache;
import com.moderndamage.control.client.ClientLegStaminaCache;
import com.moderndamage.control.config.ModClothConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.player.LocalPlayer;

/** All direct client-side MDC 1.0.32 access is confined to this adapter. */
final class ModernDamageClientAccessV1032 {
    record Snapshot(boolean armsEnabled, float armsRatio, boolean legsEnabled, float legsRatio) {
    }

    private boolean stockHudDisabled;

    Snapshot snapshot(LocalPlayer player) {
        ModClothConfig config = ModClothConfig.get();
        float armMax = (float) player.getAttributeValue(ModAttributes.MAX_ARM_STAMINA.get());
        float legMax = (float) player.getAttributeValue(ModAttributes.MAX_LEG_STAMINA.get());
        float arm = ClientArmStaminaCache.getStamina(player.getUUID());
        float leg = ClientLegStaminaCache.getStamina(player.getUUID());
        return new Snapshot(config.enableArmStamina, ratio(arm, armMax),
                config.enableLegStamina, ratio(leg, legMax));
    }

    void disableStockStaminaHudIfRequired() {
        if (stockHudDisabled || !TacticalTabletClientConfig.MDC_STAMINA_HUD_ENABLED.get()) return;
        ModClothConfig config = ModClothConfig.get();
        if (config.enableStaminaHUD) {
            config.enableStaminaHUD = false;
            AutoConfig.getConfigHolder(ModClothConfig.class).save();
        }
        stockHudDisabled = true;
    }

    private static float ratio(float value, float maximum) {
        if (!Float.isFinite(value) || !Float.isFinite(maximum) || maximum <= 0.0F) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F, value / maximum));
    }
}
