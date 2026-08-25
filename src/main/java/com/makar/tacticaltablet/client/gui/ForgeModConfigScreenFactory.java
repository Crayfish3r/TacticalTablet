package com.makar.tacticaltablet.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;

import java.util.Objects;
import java.util.Optional;

public final class ForgeModConfigScreenFactory {

    private ForgeModConfigScreenFactory() {
    }

    public static boolean isAvailable(String modId) {
        Objects.requireNonNull(modId, "modId");
        return ModList.get().getModContainerById(modId)
                .flatMap(container -> ConfigScreenHandler.getScreenFactoryFor(container.getModInfo()))
                .isPresent();
    }

    public static Optional<Screen> create(String modId, Minecraft minecraft, Screen parent) {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(parent, "parent");
        return ModList.get().getModContainerById(modId)
                .flatMap(container -> ConfigScreenHandler.getScreenFactoryFor(container.getModInfo()))
                .map(factory -> factory.apply(minecraft, parent));
    }
}
