package com.makar.tacticaltablet.client.gui;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;

import java.lang.reflect.Constructor;

final class GraphicsSettingsScreenFactory {

    private static final String EMBEDDIUM_SCREEN_CLASS =
            "me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI";

    private GraphicsSettingsScreenFactory() {
    }

    static Screen create(Screen parent, Options options) {
        try {
            Class<?> screenClass = Class.forName(EMBEDDIUM_SCREEN_CLASS);
            if (!Screen.class.isAssignableFrom(screenClass)) {
                throw new ReflectiveOperationException("Embeddium options class is not a Screen");
            }

            Constructor<?> constructor = screenClass.getConstructor(Screen.class);
            return (Screen) constructor.newInstance(parent);
        } catch (ClassNotFoundException exception) {
            TacticalTabletMod.LOGGER.debug("Embeddium is not present; opening vanilla video settings");
        } catch (ReflectiveOperationException | LinkageError exception) {
            TacticalTabletMod.LOGGER.warn(
                    "Could not open Embeddium video settings; opening vanilla video settings",
                    exception
            );
        }

        return new VideoSettingsScreen(parent, options);
    }
}
