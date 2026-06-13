package com.makar.tacticaltablet.corpse;

import com.makar.tacticaltablet.core.ModEntities;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CorpseEntityEvents {

    private CorpseEntityEvents() {
    }

    @SubscribeEvent
    public static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.PLAYER_CORPSE.get(), CorpseEntity.createAttributes().build());
    }
}
