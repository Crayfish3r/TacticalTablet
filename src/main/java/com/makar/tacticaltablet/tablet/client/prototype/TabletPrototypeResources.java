package com.makar.tacticaltablet.tablet.client.prototype;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The item uses builtin/entity directly; no legacy model wrappers or mode switch. */
@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TabletPrototypeResources {
    private TabletPrototypeResources() { }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(TabletPrototypeModel.INSTANCE);
    }
}
