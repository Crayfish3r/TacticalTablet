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
    private static final List<Probe> PROBES = List.of(
            probe("tacticaltablet", "textures/gui/tablet.png"),
            probe("tacticaltablet", "textures/gui/buttons/class_button.png"),
            override("minecraft", "textures/gui/container/inventory.png"),
            override("minecraft", "textures/gui/recipe_button.png"),
            override("minecraft", "lang/ru_ru.json"),
            override("curios", "textures/gui/inventory.png"),
            override("curios", "textures/gui/inventory_revamp.png")
    );

    private EmbeddedResourceDiagnostics() {
    }

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }

        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            for (Probe probe : PROBES) {
                resourceManager.getResource(probe.location()).ifPresentOrElse(
                        resource -> {
                            String actualSource = resource.sourcePackId();
                            if (probe.expectedSourcePackId() != null
                                    && !probe.expectedSourcePackId().equals(actualSource)) {
                                TacticalTabletMod.LOGGER.error(
                                        "[embedded-resource-audit] WRONG SOURCE {} -> {}, expected {}",
                                        probe.location(),
                                        actualSource,
                                        probe.expectedSourcePackId()
                                );
                            } else {
                                TacticalTabletMod.LOGGER.info(
                                        "[embedded-resource-audit] {} -> {}",
                                        probe.location(),
                                        actualSource
                                );
                            }
                        },
                        () -> TacticalTabletMod.LOGGER.error(
                                "[embedded-resource-audit] MISSING {}",
                                probe.location()
                        )
                );
            }
        });
    }

    private static ResourceLocation resource(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    private static Probe probe(String namespace, String path) {
        return new Probe(resource(namespace, path), null);
    }

    private static Probe override(String namespace, String path) {
        return new Probe(
                resource(namespace, path),
                EmbeddedClientResourcePack.PACK_ID
        );
    }

    private record Probe(ResourceLocation location, String expectedSourcePackId) {
    }
}
