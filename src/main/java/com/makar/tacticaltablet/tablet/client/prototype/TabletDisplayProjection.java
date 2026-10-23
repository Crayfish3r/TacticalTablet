package com.makar.tacticaltablet.tablet.client.prototype;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The same corners and final matrices used to draw the display drive ray/plane picking. */
final class TabletDisplayProjection {
    private static final Matrix4f INVERSE = new Matrix4f();
    private static TabletPrototypeModel.DisplaySurface surface;
    private static int width, height;
    record Point(double x, double y) { }
    static void capture(Matrix4f model, TabletPrototypeModel.DisplaySurface display) {
        INVERSE.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(model).invert();
        surface = display;
        var window = Minecraft.getInstance().getWindow();
        width = window.getGuiScaledWidth(); height = window.getGuiScaledHeight();
    }
    static void clear() { surface = null; }
    static Point hit(double mouseX, double mouseY) {
        var window = Minecraft.getInstance().getWindow();
        if (surface == null || width != window.getGuiScaledWidth() || height != window.getGuiScaledHeight()) return null;
        float x = (float) (2 * mouseX / width - 1), y = (float) (1 - 2 * mouseY / height);
        Vector3f origin = INVERSE.transformProject(new Vector3f(x, y, -1));
        Vector3f direction = INVERSE.transformProject(new Vector3f(x, y, 1)).sub(origin);
        Vector3f a = point(surface.corners().get(0));
        Vector3f u = point(surface.corners().get(3)).sub(a), v = point(surface.corners().get(1)).sub(a);
        Vector3f normal = new Vector3f(u).cross(v);
        float denominator = normal.dot(direction);
        if (Math.abs(denominator) < 1E-7F) return null;
        float t = normal.dot(new Vector3f(a).sub(origin)) / denominator;
        if (t < 0 || t > 1) return null;
        Vector3f p = direction.mul(t).add(origin).sub(a);
        float uu = u.dot(u), vv = v.dot(v), uv = u.dot(v), determinant = uu * vv - uv * uv;
        if (determinant < 1E-12F) return null;
        double localU = (p.dot(u) * vv - p.dot(v) * uv) / determinant;
        double localV = (p.dot(v) * uu - p.dot(u) * uv) / determinant;
        if (localU < 0 || localU > 1 || localV < 0 || localV > 1) return null;
        return new Point(localU * TabletTerminalTheme.LOGICAL_WIDTH,
                localV * TabletTerminalTheme.logicalHeight(surface));
    }
    private static Vector3f point(TabletPrototypeModel.Vec p) { return new Vector3f(p.x(), p.y(), p.z()); }
}
