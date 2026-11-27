package com.makar.tacticaltablet.tablet.client.prototype;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Owns the session; all presentation, confirmation and input remain on the model surface. */
final class TabletPrototypeScreen extends Screen {
    private TabletPageShell shell;
    TabletPrototypeScreen() { super(Component.literal("Tablet visual prototype")); }
    TabletPageShell shell() { return shell; }
    @Override protected void init() {
        var model = TabletPrototypeModel.INSTANCE.model();
        if (shell == null && model != null) shell = new TabletPageShell(model.display());
    }
    @Override public void tick() { if (shell != null) shell.tick(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        if (shell != null) {
            shell.interactive(TabletPrototypeClient.isOpen());
            shell.hover(x, y);
        }
    }
    @Override public void onClose() { TabletPrototypeClient.close(); }
    @Override public void removed() { TabletPrototypeClient.resetState(); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) onClose();
        else if (minecraft.options.keyUse.matches(key, scan)) TabletPrototypeClient.pressUse();
        return true;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (minecraft.options.keyUse.matchesMouse(button)) TabletPrototypeClient.pressUse();
        else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && shell != null && TabletPrototypeClient.isOpen()) {
            shell.interactive(true);
            shell.press(x, y);
        }
        return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (shell != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (TabletPrototypeClient.isOpen()) shell.release(x, y);
            else shell.cancelPress();
        }
        return true;
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (shell != null && TabletPrototypeClient.isOpen()) shell.scroll(x, y, delta);
        return true;
    }
}
