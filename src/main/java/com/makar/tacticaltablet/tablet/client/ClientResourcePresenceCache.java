package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Resource-presence cache invalidated atomically after every client resource reload. */
@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientResourcePresenceCache {
    private static final Map<ResourceLocation, Boolean> PRESENCE = new ConcurrentHashMap<>();

    private ClientResourcePresenceCache() {
    }

    public static boolean exists(ResourceLocation location) {
        return PRESENCE.computeIfAbsent(location,
                key -> Minecraft.getInstance().getResourceManager().getResource(key).isPresent());
    }

    public static ResourceLocation resolve(ResourceLocation preferred, ResourceLocation fallback) {
        return exists(preferred) ? preferred : fallback;
    }

    static int cachedEntries() {
        return PRESENCE.size();
    }

    static void clear() {
        PRESENCE.clear();
    }

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) ignored -> clear());
    }
}
