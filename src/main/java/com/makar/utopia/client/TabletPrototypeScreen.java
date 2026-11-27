package com.makar.utopia.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Input and lifetime for the map drawn on the tablet's physical display. */
final class TabletPrototypeScreen extends Screen {
    private final TabletMapView map = new TabletMapView();

    TabletPrototypeScreen() {
        super(Component.translatable("tablet.utopia.map"));
    }

    TabletMapView map() { return map; }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        if (minecraft.player != null && minecraft.level != null) map.tick(minecraft.level, minecraft.player);
    }

    @Override public void onClose() { TabletPrototypeClient.close(); }

    @Override
    public void removed() {
        map.close();
        TabletDisplayTarget.release();
        TabletPrototypeClient.resetState();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) onClose();
        else if (minecraft.options.keyUse.matches(key, scan)) TabletPrototypeClient.pressUse();
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (minecraft.options.keyUse.matchesMouse(button)) TabletPrototypeClient.pressUse();
        return true;
    }
}
