package com.makar.utopia.client;

import com.makar.utopia.boundary.CityZone;
import com.makar.utopia.boundary.Polygon;
import com.makar.utopia.network.MapNetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** The terrain texture and all overlays share one world X/Z to display transform. */
final class TabletMapView implements AutoCloseable {
    static final int WIDTH = 384;
    private static final int MAP_LEFT = 4;
    private static final int MAP_RIGHT = WIDTH - 4;
    private static final int MAP_TOP = 24;
    private static final int MAP_BOTTOM_MARGIN = 4;
    private static final double MIN_SCALE = 0.85;
    private static final double MAX_SCALE = 8;
    private static final double MAX_CENTER = Polygon.MAX_COORDINATE + TabletTerrain.SIZE;
    private static final int RECENTER_LEFT = 282;
    private static final int RECENTER_RIGHT = 352;
    private static final int BACKGROUND = 0xff101b20;
    private static final int UNLOADED = 0xff292c30;
    private static final int HILL_LINE = 0xff70ed80;
    private static final int HILL_FILL = 0x5262dd73;
    private static final int PORT_LINE = 0xfff4e6a5;
    private static final int PORT_FILL = 0x55f6df8c;
    private static final int PERIMETER_LINE = 0xfff0a66a;
    private static final int OUTLINE = 0xff152328;

    private final TabletTerrain terrain = new TabletTerrain();
    private final double[] intersections = new double[Polygon.MAX_VERTICES];
    private double scale = 2;
    private double centerX;
    private double centerZ;
    private boolean followPlayer = true;

    record Frame(double centerX, double centerZ, double scale, Vec3 marker, Vec3 player, float yaw,
                 MapNetwork.Geometry geometry, CityZone zone, int portSeconds, long terrainRevision) { }

    private record Projection(double x, double z, float centerY, double scale) {
        float x(double worldX) { return WIDTH / 2f + (float) ((worldX - x) * scale); }
        float y(double worldZ) { return centerY + (float) ((worldZ - z) * scale); }
        double worldX(double x) { return this.x + (x - WIDTH / 2.0) / scale; }
        double worldZ(double y) { return z + (y - centerY) / scale; }
    }

    private record Label(int x, int y) { }

    static int height(TabletPrototypeModel.DisplaySurface display) {
        return Math.round(WIDTH * display.height() / display.width());
    }

    static boolean overMap(TabletDisplayProjection.Point point, int height) {
        return point != null && point.x() >= MAP_LEFT && point.x() < MAP_RIGHT
                && point.y() >= MAP_TOP && point.y() < height - MAP_BOTTOM_MARGIN;
    }

    static boolean overRecenter(TabletDisplayProjection.Point point) {
        return point.x() >= RECENTER_LEFT && point.x() < RECENTER_RIGHT && point.y() >= 4 && point.y() < 20;
    }

    void pan(double x, double y) {
        setCenter(centerX - x / scale, centerZ - y / scale);
    }

    void zoom(TabletDisplayProjection.Point point, int height, double delta) {
        if (!Double.isFinite(delta) || delta == 0) return;
        double next = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * Math.pow(1.25, delta)));
        if (next == scale) return;
        float centerY = mapCenterY(height);
        Projection before = new Projection(centerX, centerZ, centerY, scale);
        double anchorX = before.worldX(point.x());
        double anchorZ = before.worldZ(point.y());
        double nextX = anchorX - (point.x() - WIDTH / 2.0) / next;
        double nextZ = anchorZ - (point.y() - centerY) / next;
        if (Math.abs(nextX) > MAX_CENTER || Math.abs(nextZ) > MAX_CENTER) return;
        setCenter(nextX, nextZ);
        scale = next;
    }

    void recenter() {
        followPlayer = true;
    }

    private void setCenter(double x, double z) {
        followPlayer = false;
        centerX = Math.max(-MAX_CENTER, Math.min(MAX_CENTER, x));
        centerZ = Math.max(-MAX_CENTER, Math.min(MAX_CENTER, z));
    }

    private static float mapCenterY(int height) {
        return (MAP_TOP + height - MAP_BOTTOM_MARGIN) / 2f;
    }

    void tick(ClientLevel level, LocalPlayer player) {
        terrain.tick(level, followPlayer ? player.getX() : centerX, followPlayer ? player.getZ() : centerZ);
    }

    Frame frame(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) return null;
        MapNetwork.Geometry geometry = TabletMapState.geometry();
        if (geometry != null && !geometry.dimension().equals(minecraft.level.dimension().location())) geometry = null;
        TabletMapState.Sample status = geometry == null ? null : TabletMapState.status(minecraft.level);
        Vec3 marker = player.getPosition(partialTick);
        if (followPlayer) {
            centerX = marker.x;
            centerZ = marker.z;
        }
        return new Frame(centerX, centerZ, scale, marker, player.position(), player.getViewYRot(partialTick), geometry,
                status == null ? CityZone.OUTSIDE : status.zone(),
                status == null || geometry.port() == null ? -1 : status.portSeconds(), terrain.revision());
    }

    void render(GuiGraphics graphics, Frame frame, int height, int textureWidth, int textureHeight) {
        int bottom = height - MAP_BOTTOM_MARGIN;
        Projection projection = new Projection(frame.centerX, frame.centerZ, mapCenterY(height), frame.scale);
        float markerX = projection.x(frame.marker.x);
        float markerY = projection.y(frame.marker.z);
        String timer = frame.portSeconds < 0 ? null
                : String.format(Locale.ROOT, "%02d:%02d", frame.portSeconds / 60, frame.portSeconds % 60);
        boolean timerOnMap = false;
        graphics.fill(0, 0, WIDTH, height, BACKGROUND);
        graphics.fill(MAP_LEFT, MAP_TOP, MAP_RIGHT, bottom, UNLOADED);
        graphics.flush();

        int clipLeft = Math.round((float) MAP_LEFT * textureWidth / WIDTH);
        int clipRight = Math.round((float) MAP_RIGHT * textureWidth / WIDTH);
        int clipBottom = Math.round((float) MAP_BOTTOM_MARGIN * textureHeight / height);
        int clipTop = Math.round((float) (height - MAP_TOP) * textureHeight / height);
        RenderSystem.enableScissor(clipLeft, clipBottom, clipRight - clipLeft, clipTop - clipBottom);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        try {
            drawTerrain(projection);
            if (frame.geometry != null) drawBoundaries(frame.geometry, projection, bottom);
            if (markerX >= MAP_LEFT - 12 && markerX < MAP_RIGHT + 12 && markerY >= MAP_TOP - 12 && markerY < bottom + 12) {
                drawPlayer(frame.yaw, markerX, markerY);
            }
            if (frame.geometry != null) {
                drawZoneLabel(graphics, frame.geometry.hill(), Component.translatable("tablet.utopia.hill"),
                        null, HILL_LINE, projection, bottom, markerX, markerY);
                if (frame.geometry.port() != null) {
                    Component port = Component.translatable("tablet.utopia.port");
                    boolean drawn = drawZoneLabel(graphics, frame.geometry.port(), port,
                            timer, PORT_LINE, projection, bottom, markerX, markerY);
                    timerOnMap = drawn && timer != null;
                    if (!drawn && timer != null) {
                        drawZoneLabel(graphics, frame.geometry.port(), port,
                                null, PORT_LINE, projection, bottom, markerX, markerY);
                    }
                }
                graphics.flush();
            }
        } finally {
            RenderSystem.disableScissor();
        }

        var font = Minecraft.getInstance().font;
        Component title = Component.translatable("tablet.utopia.map");
        graphics.drawString(font, title, 9, 8, 0xfff0f6f4);
        Component coordinates = Component.translatable("tablet.utopia.coordinates",
                coordinate(frame.player.x), coordinate(frame.player.y), coordinate(frame.player.z));
        int coordinatesColor = switch (frame.zone) {
            case HILL -> HILL_LINE;
            case PORT -> PORT_LINE;
            default -> 0xffd9e5e3;
        };
        graphics.drawCenteredString(font, coordinates, 184, 8, coordinatesColor);
        if (timer != null && !timerOnMap) {
            graphics.drawString(font, timer, 17 + font.width(title), 8, PORT_LINE);
        }
        graphics.fill(RECENTER_LEFT, 4, RECENTER_RIGHT, 20, 0xff2a454b);
        graphics.drawCenteredString(font, Component.translatable("tablet.utopia.recenter"),
                (RECENTER_LEFT + RECENTER_RIGHT) / 2, 8, 0xffe5f4f2);
        Component north = Component.translatable("tablet.utopia.north");
        graphics.drawString(font, north, WIDTH - 9 - font.width(north), 8, 0xffd9e5e3);
    }

    private void drawTerrain(Projection projection) {
        int id = terrain.textureId();
        if (id < 0) return;
        float x = projection.x(terrain.originX());
        float y = projection.y(terrain.originZ());
        float side = (float) (TabletTerrain.SIZE * projection.scale);
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, id);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(x, y, 0).uv(0, 0).endVertex();
        builder.vertex(x, y + side, 0).uv(0, 1).endVertex();
        builder.vertex(x + side, y + side, 0).uv(1, 1).endVertex();
        builder.vertex(x + side, y, 0).uv(1, 0).endVertex();
        BufferUploader.drawWithShader(builder.end());
    }

    private void drawBoundaries(MapNetwork.Geometry geometry, Projection projection, int bottom) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        fillZone(builder, geometry.hill(), projection, bottom, HILL_FILL);
        outline(builder, geometry.hill(), projection, 4, OUTLINE);
        outline(builder, geometry.hill(), projection, 2, HILL_LINE);
        if (geometry.port() != null) {
            fillZone(builder, geometry.port(), projection, bottom, PORT_FILL);
            outline(builder, geometry.port(), projection, 4, OUTLINE);
            outline(builder, geometry.port(), projection, 2, PORT_LINE);
        }
        outline(builder, geometry.perimeter(), projection, 5, OUTLINE);
        outline(builder, geometry.perimeter(), projection, 2.5f, PERIMETER_LINE);
        BufferUploader.drawWithShader(builder.end());
    }

    private void fillZone(BufferBuilder builder, Polygon polygon, Projection projection, int bottom, int color) {
        for (int y = MAP_TOP; y < bottom; y++) {
            int count = polygon.scanline(projection.worldZ(y + 0.5), intersections);
            for (int i = 0; i < count; i += 2) {
                float left = Math.max(MAP_LEFT, projection.x(intersections[i]));
                float right = Math.min(MAP_RIGHT, projection.x(intersections[i + 1]));
                if (left >= right) continue;
                quad(builder, left, y, left, y + 1, right, y + 1, right, y, color);
            }
        }
    }

    private boolean drawZoneLabel(GuiGraphics graphics, Polygon polygon, Component name, String timer, int color,
                                  Projection projection, int bottom, float markerX, float markerY) {
        Font font = Minecraft.getInstance().font;
        int width = timer == null ? font.width(name) : Math.max(font.width(name), font.width(timer));
        int labelHeight = font.lineHeight * (timer == null ? 1 : 2);
        Label label = findLabel(polygon, projection, bottom, width, labelHeight, markerX, markerY);
        if (label == null) return false;
        graphics.drawCenteredString(font, name, label.x, label.y, color);
        if (timer != null) graphics.drawCenteredString(font, timer, label.x, label.y + font.lineHeight, color);
        return true;
    }

    private Label findLabel(Polygon polygon, Projection projection, int bottom, int width, int height,
                            float markerX, float markerY) {
        Label best = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        int halfWidth = (width + 1) / 2 + 2;
        for (int y = MAP_TOP + 2; y + height + 2 < bottom; y++) {
            int count = polygon.scanline(projection.worldZ(y + height / 2.0), intersections);
            for (int i = 0; i < count; i += 2) {
                float left = Math.max(MAP_LEFT, projection.x(intersections[i]));
                float right = Math.min(MAP_RIGHT, projection.x(intersections[i + 1]));
                int firstX = (int) Math.ceil(left) + halfWidth;
                int lastX = (int) Math.floor(right) - halfWidth;
                if (firstX > lastX) continue;
                float score = right - left - Math.abs(y + height / 2f - (projection.centerY - 23)) * 0.3f;
                if (score <= bestScore) continue;
                int x = (firstX + lastX) / 2;
                if (y < markerY + 13 && y + height > markerY - 13 && Math.abs(x - markerX) < halfWidth + 13) {
                    int beforeMarker = (int) Math.floor(markerX - halfWidth - 13);
                    int afterMarker = (int) Math.ceil(markerX + halfWidth + 13);
                    if (beforeMarker < firstX && afterMarker > lastX) continue;
                    x = beforeMarker >= firstX && (afterMarker > lastX || x - beforeMarker <= afterMarker - x)
                            ? beforeMarker : afterMarker;
                }
                if (polygon.containsRectangle(projection.worldX(x - halfWidth), projection.worldZ(y - 1),
                        projection.worldX(x + halfWidth), projection.worldZ(y + height + 1))) {
                    best = new Label(x, y);
                    bestScore = score;
                }
            }
        }
        return best;
    }

    private static void outline(BufferBuilder builder, Polygon polygon, Projection projection, float width, int color) {
        var points = polygon.vertices();
        for (int i = 0; i < points.size(); i++) {
            Polygon.Point a = points.get(i);
            Polygon.Point b = points.get((i + 1) % points.size());
            float x = projection.x(a.x()), y = projection.y(a.z());
            float endX = projection.x(b.x()), endY = projection.y(b.z());
            float dx = endX - x, dy = endY - y;
            double length = Math.hypot(dx, dy);
            if (length == 0) continue;
            float halfX = (float) (-dy * width / (2 * length));
            float halfY = (float) (dx * width / (2 * length));
            quad(builder, x + halfX, y + halfY, x - halfX, y - halfY,
                    endX - halfX, endY - halfY, endX + halfX, endY + halfY, color);
        }
    }

    private static void drawPlayer(float yaw, float x, float y) {
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        double radians = Math.toRadians(yaw);
        float dx = (float) -Math.sin(radians);
        float dy = (float) Math.cos(radians);
        marker(builder, x, y, dx, dy, 11, OUTLINE);
        marker(builder, x, y, dx, dy, 8, 0xfff4fbff);
        BufferUploader.drawWithShader(builder.end());
    }

    private static void marker(BufferBuilder builder, float x, float y, float dx, float dy, float size, int color) {
        vertex(builder, x + dx * size, y + dy * size, color);
        vertex(builder, x - (dx + dy) * size * 0.62f, y - (dy - dx) * size * 0.62f, color);
        vertex(builder, x - (dx - dy) * size * 0.62f, y - (dy + dx) * size * 0.62f, color);
    }

    private static void quad(BufferBuilder builder, float ax, float ay, float bx, float by,
                             float cx, float cy, float dx, float dy, int color) {
        vertex(builder, ax, ay, color);
        vertex(builder, bx, by, color);
        vertex(builder, cx, cy, color);
        vertex(builder, dx, dy, color);
    }

    private static void vertex(BufferBuilder builder, float x, float y, int color) {
        builder.vertex(x, y, 0).color(color >> 16 & 0xff, color >> 8 & 0xff, color & 0xff, color >>> 24).endVertex();
    }

    private static String coordinate(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    @Override public void close() { terrain.close(); }
}
