package com.makar.utopia.client;

import com.makar.utopia.boundary.Polygon;
import com.makar.utopia.network.MapNetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
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
    private static final float SCALE = 2;
    private static final int BACKGROUND = 0xff101b20;
    private static final int UNLOADED = 0xff292c30;
    private static final int HILL_LINE = 0xff70ed80;
    private static final int HILL_FILL = 0x5262dd73;
    private static final int PERIMETER_LINE = 0xffffca74;
    private static final int OUTLINE = 0xff152328;

    private final TabletTerrain terrain = new TabletTerrain();
    private final double[] intersections = new double[Polygon.MAX_VERTICES];

    record Frame(Vec3 center, Vec3 player, float yaw, MapNetwork.Geometry geometry, boolean insideHill,
                 long terrainRevision) { }

    private record Projection(double x, double z, float centerY) {
        float x(double worldX) { return WIDTH / 2f + (float) ((worldX - x) * SCALE); }
        float y(double worldZ) { return centerY + (float) ((worldZ - z) * SCALE); }
        double worldZ(float y) { return z + (y - centerY) / SCALE; }
    }

    private record Label(float x, float y) { }

    static int height(TabletPrototypeModel.DisplaySurface display) {
        return Math.round(WIDTH * display.height() / display.width());
    }

    void tick(ClientLevel level, LocalPlayer player) {
        terrain.tick(level, player.chunkPosition());
    }

    Frame frame(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) return null;
        MapNetwork.Geometry geometry = TabletMapState.geometry();
        if (geometry != null && !geometry.dimension().equals(minecraft.level.dimension().location())) geometry = null;
        return new Frame(player.getPosition(partialTick), player.position(), player.getViewYRot(partialTick), geometry,
                geometry != null && TabletMapState.insideHill(), terrain.revision());
    }

    void render(GuiGraphics graphics, Frame frame, int height, int textureWidth, int textureHeight) {
        int bottom = height - MAP_BOTTOM_MARGIN;
        Projection projection = new Projection(frame.center.x, frame.center.z, (MAP_TOP + bottom) / 2f);
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
            Label label = frame.geometry == null ? null : drawBoundaries(frame.geometry, projection, bottom);
            drawPlayer(frame.yaw, projection.centerY);
            if (label != null) {
                graphics.drawCenteredString(Minecraft.getInstance().font, Component.translatable("tablet.utopia.hill"),
                        Math.round(label.x), Math.round(label.y) - 4, 0xffccffd0);
                graphics.flush();
            }
        } finally {
            RenderSystem.disableScissor();
        }

        var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable("tablet.utopia.map"), 9, 8, 0xfff0f6f4);
        Component coordinates = Component.translatable("tablet.utopia.coordinates",
                coordinate(frame.player.x), coordinate(frame.player.y), coordinate(frame.player.z));
        graphics.drawCenteredString(font, coordinates, WIDTH / 2, 8, frame.insideHill ? HILL_LINE : 0xffd9e5e3);
        Component north = Component.translatable("tablet.utopia.north");
        graphics.drawString(font, north, WIDTH - 9 - font.width(north), 8, 0xffd9e5e3);
    }

    private void drawTerrain(Projection projection) {
        int id = terrain.textureId();
        if (id < 0) return;
        float x = projection.x(terrain.originX());
        float y = projection.y(terrain.originZ());
        float side = TabletTerrain.SIZE * SCALE;
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

    private Label drawBoundaries(MapNetwork.Geometry geometry, Projection projection, int bottom) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Label label = fillHill(builder, geometry.hill(), projection, bottom);
        outline(builder, geometry.hill(), projection, 4, OUTLINE);
        outline(builder, geometry.hill(), projection, 2, HILL_LINE);
        outline(builder, geometry.perimeter(), projection, 5, OUTLINE);
        outline(builder, geometry.perimeter(), projection, 2.5f, PERIMETER_LINE);
        BufferUploader.drawWithShader(builder.end());
        return label;
    }

    private Label fillHill(BufferBuilder builder, Polygon hill, Projection projection, int bottom) {
        Label best = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (int y = MAP_TOP; y < bottom; y++) {
            int count = hill.scanline(projection.worldZ(y + 0.5f), intersections);
            for (int i = 0; i < count; i += 2) {
                float left = Math.max(MAP_LEFT, projection.x(intersections[i]));
                float right = Math.min(MAP_RIGHT, projection.x(intersections[i + 1]));
                if (left >= right) continue;
                quad(builder, left, y, left, y + 1, right, y + 1, right, y, HILL_FILL);
                float labelX = (left + right) / 2;
                float labelY = y + 0.5f;
                // Leave room for the player arrow at the map center, even in a broad part of the district.
                float arrowClearance = Math.max(Math.abs(labelX - WIDTH / 2f) - 16,
                        Math.abs(labelY - projection.centerY));
                float score = right - left - Math.abs(labelY - (projection.centerY - 23)) * 0.3f
                        - Math.max(0, 23 - arrowClearance) * 4;
                if (score > bestScore) {
                    best = new Label(labelX, labelY);
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

    private static void drawPlayer(float yaw, float y) {
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        double radians = Math.toRadians(yaw);
        float dx = (float) -Math.sin(radians);
        float dy = (float) Math.cos(radians);
        marker(builder, WIDTH / 2f, y, dx, dy, 11, OUTLINE);
        marker(builder, WIDTH / 2f, y, dx, dy, 8, 0xfff4fbff);
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
