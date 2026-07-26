package com.makar.tacticaltablet.client;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;

@Mod.EventBusSubscriber(
        modid = TacticalTabletMod.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class EmbeddedClientResourcePack {
    public static final String PACK_ID = "tacticaltablet_overrides";
    private static final String PACK_DIRECTORY = "tacticaltablet_overrides";

    private EmbeddedClientResourcePack() {
    }

    @SubscribeEvent
    public static void addPackFinder(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }

        Path packRoot = ModList.get()
                .getModFileById(TacticalTabletMod.MODID)
                .getFile()
                .findResource("resourcepacks", PACK_DIRECTORY);
        Pack.ResourcesSupplier resources =
                id -> new PathPackResources(id, packRoot, true);
        Pack.Info info = Pack.readPackInfo(PACK_ID, resources);
        if (info == null) {
            TacticalTabletMod.LOGGER.error(
                    "Unable to register required embedded client resource pack {} at {}",
                    PACK_ID,
                    packRoot
            );
            return;
        }

        Pack pack = Pack.create(
                PACK_ID,
                Component.literal("Tactical Tablet Overrides"),
                true,
                resources,
                info,
                PackType.CLIENT_RESOURCES,
                Pack.Position.TOP,
                true,
                PackSource.BUILT_IN
        );
        event.addRepositorySource(consumer -> consumer.accept(pack));
    }
}
