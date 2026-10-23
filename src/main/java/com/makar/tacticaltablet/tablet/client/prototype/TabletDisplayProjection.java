package com.makar.tacticaltablet.tablet.client.prototype;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** One model-space cursor ray drives both display and independent hardware mesh picking. */
final class TabletDisplayProjection {
    private static final Matrix4f INVERSE = new Matrix4f();
    private static TabletPrototypeModel.Model model;
    private static int width, height;
    record Point(double x, double y) { }
    record Hit(Point display, String hardwareKey) { }
    private record Ray(Vector3f origin, Vector3f direction) { }
    private record DisplayHit(Point point, float distance) { }
    private static final Hit MISS = new Hit(null, null);

    static void capture(Matrix4f transform, TabletPrototypeModel.Model prepared) {
        INVERSE.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(transform).invert();
        model = prepared;
        var window = Minecraft.getInstance().getWindow();
        width = window.getGuiScaledWidth(); height = window.getGuiScaledHeight();
    }
    static void clear() { model = null; }

    private static Ray ray(double mouseX, double mouseY) {
        var window = Minecraft.getInstance().getWindow();
        if (model == null || width != window.getGuiScaledWidth() || height != window.getGuiScaledHeight()) return null;
        float x = (float) (2 * mouseX / width - 1), y = (float) (1 - 2 * mouseY / height);
        Vector3f origin = INVERSE.transformProject(new Vector3f(x, y, -1));
        Vector3f direction = INVERSE.transformProject(new Vector3f(x, y, 1)).sub(origin);
        return new Ray(origin, direction);
    }

    static Point hit(double mouseX, double mouseY) { return pick(mouseX, mouseY).display(); }

    static Hit pick(double mouseX, double mouseY) {
        Ray ray = ray(mouseX, mouseY);
        if (ray == null) return MISS;
        DisplayHit display = display(ray);
        float nearest = display == null ? Float.POSITIVE_INFINITY : display.distance;
        String key = null;
        // Hardware picking does not require a display intersection. Quads retain every part rotation.
        for (var entry : model.hardwareKeys().entrySet()) {
            var mesh = entry.getValue();
            for (int i = 0; i < mesh.size(); i += 4) {
                float a = triangle(ray, mesh.get(i), mesh.get(i + 1), mesh.get(i + 2));
                float b = triangle(ray, mesh.get(i), mesh.get(i + 2), mesh.get(i + 3));
                float distance = Math.min(a, b);
                if (distance < nearest) { nearest = distance; key = entry.getKey(); }
            }
        }
        if (key != null) return new Hit(null, key);
        return display == null ? MISS : new Hit(display.point, null);
    }

    private static DisplayHit display(Ray ray) {
        var surface = model.display();
        Vector3f origin = ray.origin, direction = ray.direction;
        Vector3f a = point(surface.corners().get(0));
        Vector3f u = point(surface.corners().get(3)).sub(a), v = point(surface.corners().get(1)).sub(a);
        Vector3f normal = new Vector3f(u).cross(v);
        float denominator = normal.dot(direction);
        if (Math.abs(denominator) < 1E-7F) return null;
        float t = normal.dot(new Vector3f(a).sub(origin)) / denominator;
        if (t < 0 || t > 1) return null;
        Vector3f p = new Vector3f(direction).mul(t).add(origin).sub(a);
        float uu = u.dot(u), vv = v.dot(v), uv = u.dot(v), determinant = uu * vv - uv * uv;
        if (determinant < 1E-12F) return null;
        double localU = (p.dot(u) * vv - p.dot(v) * uv) / determinant;
        double localV = (p.dot(v) * uu - p.dot(u) * uv) / determinant;
        if (localU < 0 || localU > 1 || localV < 0 || localV > 1) return null;
        return new DisplayHit(new Point(localU * TabletTerminalTheme.LOGICAL_WIDTH,
                localV * TabletTerminalTheme.logicalHeight(surface)), t);
    }

    private static float triangle(Ray ray, TabletPrototypeModel.Vertex a, TabletPrototypeModel.Vertex b,
                                  TabletPrototypeModel.Vertex c) {
        float ex = b.x() - a.x(), ey = b.y() - a.y(), ez = b.z() - a.z();
        float fx = c.x() - a.x(), fy = c.y() - a.y(), fz = c.z() - a.z();
        var d = ray.direction; var o = ray.origin;
        float px = d.y * fz - d.z * fy, py = d.z * fx - d.x * fz, pz = d.x * fy - d.y * fx;
        float determinant = ex * px + ey * py + ez * pz;
        if (Math.abs(determinant) < 1E-9F) return Float.POSITIVE_INFINITY;
        float tx = o.x - a.x(), ty = o.y - a.y(), tz = o.z - a.z();
        float u = (tx * px + ty * py + tz * pz) / determinant;
        if (u < 0 || u > 1) return Float.POSITIVE_INFINITY;
        float qx = ty * ez - tz * ey, qy = tz * ex - tx * ez, qz = tx * ey - ty * ex;
        float v = (d.x * qx + d.y * qy + d.z * qz) / determinant;
        if (v < 0 || u + v > 1) return Float.POSITIVE_INFINITY;
        float t = (fx * qx + fy * qy + fz * qz) / determinant;
        return t >= 0 && t <= 1 ? t : Float.POSITIVE_INFINITY;
    }
    private static Vector3f point(TabletPrototypeModel.Vec p) { return new Vector3f(p.x(), p.y(), p.z()); }
}
