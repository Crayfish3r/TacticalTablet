package com.makar.tacticaltablet.tablet.client.prototype;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Input ownership only: no widgets, backdrop, sounds, packets, or pause. */
final class TabletPrototypeScreen extends Screen {
    TabletPrototypeScreen() { super(Component.literal("Tablet visual prototype")); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) { }
    @Override public void onClose() { TabletPrototypeClient.close(); }
    @Override public void removed() { TabletPrototypeClient.resetState(); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) onClose();
        else if (minecraft.options.keyUse.matches(key, scan)) TabletPrototypeClient.pressUse();
        return true;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (minecraft.options.keyUse.matchesMouse(button)) TabletPrototypeClient.pressUse();
        return true;
    }
}
