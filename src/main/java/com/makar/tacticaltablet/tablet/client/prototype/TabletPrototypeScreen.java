package com.makar.tacticaltablet.tablet.client.prototype;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Owns the session; all presentation, confirmation and input remain on the model surface. */
final class TabletPrototypeScreen extends Screen {
    private TabletPreparationPage page;
    TabletPrototypeScreen() { super(Component.literal("Tablet visual prototype")); }
    TabletPreparationPage page() { return page; }
    @Override protected void init() {
        var model = TabletPrototypeModel.INSTANCE.model();
        if (page == null && model != null) page = new TabletPreparationPage(model.display());
    }
    @Override public void tick() { if (page != null) page.tick(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        if (page != null) {
            page.interactive(TabletPrototypeClient.isOpen());
            page.hover(TabletDisplayProjection.hit(x, y), TabletPrototypeClient.isOpen());
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
        else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && page != null && TabletPrototypeClient.isOpen())
            page.press(TabletDisplayProjection.hit(x, y));
        return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (page != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (TabletPrototypeClient.isOpen()) page.release(TabletDisplayProjection.hit(x, y));
            else page.cancelPress();
        }
        return true;
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (page != null && TabletPrototypeClient.isOpen()) page.scroll(TabletDisplayProjection.hit(x, y), delta);
        return true;
    }
}
