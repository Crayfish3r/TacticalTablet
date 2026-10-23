package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.client.SpectatorCameraClientState;
import com.makar.tacticaltablet.client.SpectatorHudClientState;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.integration.moderndamage.client.ModernDamageClientState;
import com.makar.tacticaltablet.prefix.PrefixClientState;
import com.makar.tacticaltablet.tablet.client.prototype.TabletPrototypeClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Tablet input now has one owner; match snapshots never open a functional GUI. */
@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, value = Dist.CLIENT)
public class ClientEvents {
    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isUseItem()) TabletPrototypeClient.onUse(event);
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ChaosClientState.clear();
        PrefixClientState.clear();
        SpectatorCameraClientState.clear();
        SpectatorHudClientState.clear();
        ModernDamageClientState.clear();
    }
}
