package com.makar.tacticaltablet.core;

import com.makar.tacticaltablet.tablet.TacticalTabletItem;
import com.makar.tacticaltablet.game.contract.ContractTrackerItem;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TacticalTabletMod.MODID);

    public static final RegistryObject<Item> TACTICAL_TABLET = ITEMS.register("tactical_tablet", 
            () -> new TacticalTabletItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> CONTRACT_TRACKER = ITEMS.register("contract_tracker",
            () -> new ContractTrackerItem(new Item.Properties().stacksTo(1)));
}
