package com.makar.tacticaltablet.client;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(
        modid = TacticalTabletMod.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class EmbeddedResourceDiagnostics {
    private static final String ENABLE_PROPERTY = "tacticaltablet.verifyEmbeddedResources";
    private static final List<ResourceLocation> PROBES = List.of(
            resource("tacticaltablet", "textures/gui/tablet.png"),
            resource("tacticaltablet", "textures/gui/buttons/class_button.png"),
            resource("minecraft", "textures/gui/container/inventory.png"),
            resource("minecraft", "textures/gui/recipe_button.png"),
            resource("minecraft", "font/default.json"),
            resource("minecraft", "lang/ru_ru.json"),
            resource("curios", "textures/gui/inventory.png"),
            resource("curios", "textures/gui/inventory_revamp.png"),
            resource("deluxewarfare", "font/tactical_mono.ttf")
    );

    private EmbeddedResourceDiagnostics() {
    }

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }

        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            for (ResourceLocation probe : PROBES) {
                resourceManager.getResource(probe).ifPresentOrElse(
                        resource -> TacticalTabletMod.LOGGER.info(
                                "[embedded-resource-audit] {} -> {}",
                                probe,
                                resource.sourcePackId()
                        ),
                        () -> TacticalTabletMod.LOGGER.error(
                                "[embedded-resource-audit] MISSING {}",
                                probe
                        )
                );
            }
        });
    }

    private static ResourceLocation resource(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
