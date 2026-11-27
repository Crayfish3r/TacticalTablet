package com.makar.utopia.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Ray picking on the same quad and transform used to draw the first-person display. */
final class TabletDisplayProjection {
    record Point(double x, double y) { }

    private final Matrix4f clipToModel;
    private final Vector3f topLeft;
    private final Vector3f right;
    private final Vector3f down;
    private final Vector3f normal;
    private final int guiWidth;
    private final int guiHeight;
    private final int framebufferWidth;
    private final int framebufferHeight;
    private final int displayHeight;

    TabletDisplayProjection(Matrix4f pose, TabletPrototypeModel.DisplaySurface display, Window window) {
        clipToModel = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix())
                .mul(pose).invert();
        var corners = display.corners();
        topLeft = vector(corners.get(0));
        right = vector(corners.get(3)).sub(topLeft);
        down = vector(corners.get(1)).sub(topLeft);
        normal = new Vector3f(right).cross(down);
        guiWidth = window.getGuiScaledWidth();
        guiHeight = window.getGuiScaledHeight();
        framebufferWidth = window.getWidth();
        framebufferHeight = window.getHeight();
        displayHeight = TabletMapView.height(display);
    }

    Point pick(double mouseX, double mouseY, Window window) {
        if (guiWidth <= 0 || guiHeight <= 0 || guiWidth != window.getGuiScaledWidth()
                || guiHeight != window.getGuiScaledHeight() || framebufferWidth != window.getWidth()
                || framebufferHeight != window.getHeight()) return null;
        // Screen callbacks already use GUI-scaled coordinates. Convert their top-left origin to NDC.
        float x = (float) (2 * mouseX / guiWidth - 1);
        float y = (float) (1 - 2 * mouseY / guiHeight);
        Vector3f near = unproject(x, y, -1);
        Vector3f direction = unproject(x, y, 0).sub(near);
        float denominator = normal.dot(direction);
        if (Math.abs(denominator) < 1e-9f) return null;
        float distance = normal.dot(new Vector3f(topLeft).sub(near)) / denominator;
        if (!Float.isFinite(distance) || distance < 0) return null;
        Vector3f local = near.fma(distance, direction).sub(topLeft);
        float u = local.dot(right) / right.lengthSquared();
        float v = local.dot(down) / down.lengthSquared();
        if (u < 0 || u > 1 || v < 0 || v > 1) return null;
        return new Point(u * TabletMapView.WIDTH, v * displayHeight);
    }

    private Vector3f unproject(float x, float y, float z) {
        Vector4f point = clipToModel.transform(new Vector4f(x, y, z, 1));
        return new Vector3f(point.x / point.w, point.y / point.w, point.z / point.w);
    }

    private static Vector3f vector(TabletPrototypeModel.Vec point) {
        return new Vector3f(point.x(), point.y(), point.z());
    }
}
