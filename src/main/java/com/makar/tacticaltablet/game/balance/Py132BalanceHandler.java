package com.makar.tacticaltablet.game.balance;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.core.TacticalTabletServerConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = TacticalTabletMod.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE
)
public final class Py132BalanceHandler {

    private Py132BalanceHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        ResourceLocation damageType = event.getSource()
                .typeHolder()
                .unwrapKey()
                .map(ResourceKey::location)
                .orElse(null);
        boolean enabled = TacticalTabletServerConfig.isPy132BalanceEnabled();

        if (!Py132BalancePolicy.appliesTo(damageType, enabled)) {
            return;
        }

        event.setAmount(Py132BalancePolicy.adjustDamage(
                damageType,
                event.getAmount(),
                enabled,
                TacticalTabletServerConfig.getPy132DamageMultiplier()
        ));
    }
}
