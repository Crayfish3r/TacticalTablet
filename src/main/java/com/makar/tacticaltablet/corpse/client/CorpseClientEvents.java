package com.makar.tacticaltablet.corpse.client;

import com.makar.tacticaltablet.core.ModEntities;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CorpseClientEvents {

    private CorpseClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.PLAYER_CORPSE.get(), CorpseRenderer::new);
    }
}
