package com.makar.tacticaltablet.client;

import com.makar.tacticaltablet.core.TacticalTabletMod;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, value = Dist.CLIENT)
public class ClientAntiCheatEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) return;
        if (mc.level == null) return;

        if (mc.getEntityRenderDispatcher().shouldRenderHitBoxes()) {
            mc.getEntityRenderDispatcher().setRenderHitBoxes(false);
        }
    }
}
