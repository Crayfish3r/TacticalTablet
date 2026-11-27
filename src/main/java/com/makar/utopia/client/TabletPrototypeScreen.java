package com.makar.utopia.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/** Input and lifetime for the map drawn on the tablet's physical display. */
final class TabletPrototypeScreen extends Screen {
    private final TabletMapView map = new TabletMapView();
    private TabletDisplayProjection displayProjection;
    private int displayHeight;
    private boolean draggingMap;
    private boolean movedDrag;
    private TabletDisplayProjection.Point dragPoint;

    TabletPrototypeScreen() {
        super(Component.translatable("tablet.utopia.map"));
    }

    TabletMapView map() { return map; }

    void captureDisplay(Matrix4f pose, TabletPrototypeModel.DisplaySurface display) {
        displayProjection = new TabletDisplayProjection(pose, display, minecraft.getWindow());
        displayHeight = TabletMapView.height(display);
    }

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
    public boolean keyReleased(int key, int scan, int modifiers) {
        if (minecraft.options.keyUse.matches(key, scan)) TabletPrototypeClient.releaseUse();
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        var point = pick(x, y);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && point != null) {
            if (TabletMapView.overRecenter(point)) {
                map.recenter();
                return true;
            }
            if (TabletMapView.overMap(point, displayHeight)) {
                draggingMap = true;
                movedDrag = false;
                dragPoint = point;
                return true;
            }
        }
        if (minecraft.options.keyUse.matchesMouse(button)) TabletPrototypeClient.pressUse();
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !draggingMap) return false;
        var point = pick(x, y);
        if (!TabletMapView.overMap(point, displayHeight)) {
            dragPoint = null;
            movedDrag = true;
        } else if (dragPoint == null) {
            dragPoint = point;
        } else {
            double deltaX = point.x() - dragPoint.x();
            double deltaY = point.y() - dragPoint.y();
            if (movedDrag || Math.hypot(deltaX, deltaY) >= 1.5) {
                map.pan(deltaX, deltaY);
                movedDrag = true;
                dragPoint = point;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        boolean use = minecraft.options.keyUse.matchesMouse(button);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingMap) {
            if (use && !movedDrag && TabletMapView.overMap(pick(x, y), displayHeight)) onClose();
            draggingMap = false;
            dragPoint = null;
        }
        if (use) TabletPrototypeClient.releaseUse();
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        var point = pick(x, y);
        if (!TabletMapView.overMap(point, displayHeight)) return false;
        map.zoom(point, displayHeight, delta);
        return true;
    }

    private TabletDisplayProjection.Point pick(double x, double y) {
        return TabletPrototypeClient.isOpen() && displayProjection != null
                ? displayProjection.pick(x, y, minecraft.getWindow()) : null;
    }
}
