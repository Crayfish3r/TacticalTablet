package com.makar.utopia;

import com.makar.utopia.network.MapNetwork;
import com.makar.utopia.tablet.TabletItem;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(Utopia.MOD_ID)
public final class Utopia {
    public static final String MOD_ID = "utopia";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> TABLET = ITEMS.register("tablet", TabletItem::new);

    public Utopia(FMLJavaModLoadingContext context) {
        ITEMS.register(context.getModEventBus());
        MapNetwork.register();
    }
}
