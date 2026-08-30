package com.makar.tacticaltablet.client;

import com.makar.tacticaltablet.client.gui.JoinServerConfigScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/** Registers TacticalTablet's own Config button only on the physical client. */
public final class ClientConfigScreenRegistration {

    private ClientConfigScreenRegistration() {
    }

    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(JoinServerConfigScreen::new)
        );
    }
}
