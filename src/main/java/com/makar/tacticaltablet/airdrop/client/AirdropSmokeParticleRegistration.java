package com.makar.tacticaltablet.airdrop.client;

import com.makar.tacticaltablet.core.ModParticles;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AirdropSmokeParticleRegistration {
    private AirdropSmokeParticleRegistration() {
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.AIRDROP_SMOKE.get(), AirdropSmokeParticle.Provider::new);
    }
}
