package com.makar.tacticaltablet.core;

import com.makar.tacticaltablet.casino.CasinoMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, TacticalTabletMod.MODID);
    public static final RegistryObject<MenuType<CasinoMenu>> CASINO = MENUS.register("casino", () -> IForgeMenuType.create(CasinoMenu::new));
    private ModMenuTypes() { }
}
