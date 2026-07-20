package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.tablet.net.ContractSelectTargetPacket;
import com.makar.tacticaltablet.tablet.net.ContractSelectionStatePacket;
import com.makar.tacticaltablet.tablet.net.ContractTrackerStatePacket;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.tablet.net.TrackerWatchPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.List;

public class ContractTrackerScreen extends Screen {

    private static final ResourceLocation PANEL =
            new ResourceLocation("tacticaltablet", "textures/gui/contract_gui.png");
    private static final int UI_WIDTH = 220;
    private static final int UI_HEIGHT = 300;
    private static final int MAP_SIZE = 148;

    private int lastTargetCount = -1;
    private boolean lastSelectionMode;
    private boolean watching;

    public ContractTrackerScreen() {
        super(Component.literal("Контрактный трекер"));
    }

    @Override
    protected void init() {
        startWatching();
        rebuildSelectionButtons();
    }

    @Override
    public void removed() {
        stopWatching();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        boolean selectionMode = isSelectionMode();
        int targetCount = ContractClientState.getTargets().size();
        if (selectionMode != lastSelectionMode || targetCount != lastTargetCount) {
            rebuildSelectionButtons();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int x = (this.width - UI_WIDTH) / 2;
        int y = (this.height - UI_HEIGHT) / 2;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, PANEL);
        g.blit(PANEL, x, y, 0, 0, UI_WIDTH, UI_HEIGHT, UI_WIDTH, UI_HEIGHT);

        if (!ContractClientState.isTrackerActive()) {
            drawSelection(g, x, y);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        drawInfo(g, x, y);
        drawContractMap(g, x + 36, y + 110, MAP_SIZE, partialTick);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private boolean isSelectionMode() {
        return !ContractClientState.isTrackerActive()
                && ContractClientState.isSoloMode()
                && ContractClientState.isSelectionActive()
                && !ContractClientState.hasActiveContract();
    }

    private void startWatching() {
        if (watching) return;
        watching = true;
        if (canSendWatchPacket()) {
            PacketHandler.sendToServer(new TrackerWatchPacket(true));
        }
    }

    private void stopWatching() {
        if (!watching) return;
        watching = false;
        if (canSendWatchPacket()) {
            PacketHandler.sendToServer(new TrackerWatchPacket(false));
        }
    }

    private boolean canSendWatchPacket() {
        return Minecraft.getInstance().getConnection() != null;
    }

    private void rebuildSelectionButtons() {
        this.clearWidgets();
        lastSelectionMode = isSelectionMode();
        lastTargetCount = ContractClientState.getTargets().size();

        if (!lastSelectionMode) return;

        int x = (this.width - UI_WIDTH) / 2;
        int y = (this.height - UI_HEIGHT) / 2;
        List<ContractSelectionStatePacket.TargetEntry> targets = ContractClientState.getTargets();
        int count = Math.min(8, targets.size());

        for (int i = 0; i < count; i++) {
            ContractSelectionStatePacket.TargetEntry target = targets.get(i);
            this.addRenderableWidget(new TrackerTargetButton(x + 28, y + 78 + i * 19, 164, 16, target));
        }
    }

    private void drawSelection(GuiGraphics g, int x, int y) {
        var font = Minecraft.getInstance().font;
        g.drawCenteredString(font, "КОНТРАКТЫ", x + UI_WIDTH / 2, y + 18, 0xFF9FC36A);

        if (!ContractClientState.isSoloMode()) {
            g.drawCenteredString(font, "Контракты доступны только в соло.", x + UI_WIDTH / 2, y + 132, 0xFFFFD966);
            return;
        }

        if (ContractClientState.hasActiveContract()) {
            g.drawCenteredString(font, "Контракт активен.", x + UI_WIDTH / 2, y + 126, 0xFF66FF66);
            g.drawCenteredString(font, "Открой трекер еще раз.", x + UI_WIDTH / 2, y + 142, 0xFFAAAAAA);
            return;
        }

        if (!ContractClientState.isSelectionActive()) {
            g.drawCenteredString(font, "Выбор контракта закрыт.", x + UI_WIDTH / 2, y + 132, 0xFFAAAAAA);
            return;
        }

        g.drawCenteredString(font, "Осталось: " + ContractClientState.getSelectionSecondsLeft() + "с",
                x + UI_WIDTH / 2, y + 44, 0xFFAAAAAA);

        long cooldown = ContractClientState.getCooldownLeftMs();
        if (cooldown > 0L) {
            g.drawCenteredString(font, "Трекер: " + formatTime(cooldown),
                    x + UI_WIDTH / 2, y + 56, 0xFFFFD966);
        }

        if (ContractClientState.getTargets().isEmpty()) {
            g.drawCenteredString(font, "Нет доступных целей.", x + UI_WIDTH / 2, y + 132, 0xFFAAAAAA);
        } else {
            g.drawCenteredString(font, "Выбери одну цель", x + UI_WIDTH / 2, cooldown > 0L ? y + 68 : y + 60, 0xFFCCCCCC);
        }
    }

    private void drawInfo(GuiGraphics g, int x, int y) {
        var font = Minecraft.getInstance().font;
        int left = x + 34;
        int top = y + 35;
        List<ContractTrackerStatePacket.TargetEntry> targets = ContractClientState.getTrackerTargets();

        g.drawCenteredString(font, "КОНТРАКТЫ КОМАНДЫ", x + UI_WIDTH / 2, y + 18, 0xFF9FC36A);

        int visible = Math.min(4, targets.size());
        for (int i = 0; i < visible; i++) {
            ContractTrackerStatePacket.TargetEntry target = targets.get(i);
            int color = ContractClientState.difficultyColor(target.difficulty());
            String line = (i + 1) + ". " + target.name() + " | " + target.selectedClass()
                    + " | " + target.reward();
            g.drawString(font, font.plainSubstrByWidth(line, 152), left, top + i * 13, color, false);
        }

        if (targets.size() > visible) {
            g.drawString(font, "+" + (targets.size() - visible) + " целей", left, top + visible * 13,
                    0xFFAAAAAA, false);
        }
        g.drawString(font, "Сигнал: " + ContractClientState.getSignalSecondsLeft() + "с",
                left, top + 60, 0xFF9FC36A, false);
    }

    private void drawContractMap(GuiGraphics g, int mapX, int mapY, int size, float partialTick) {
        g.fill(mapX, mapY, mapX + size, mapY + size, 0xAA050805);

        int gridColor = 0x6614260F;
        for (int i = 1; i < 4; i++) {
            int p = mapX + i * size / 4;
            g.fill(p, mapY + 1, p + 1, mapY + size - 1, gridColor);
            int q = mapY + i * size / 4;
            g.fill(mapX + 1, q, mapX + size - 1, q + 1, gridColor);
        }

        drawRectOutline(g, mapX, mapY, size, size, 0xFFAAAAAA);
        drawRectOutline(g, mapX + 2, mapY + 2, size - 4, size - 4, 0xFFCCCCCC);

        var font = Minecraft.getInstance().font;
        g.drawCenteredString(font, "N", mapX + size / 2, mapY - 10, 0xFFAAAAAA);
        g.drawCenteredString(font, "S", mapX + size / 2, mapY + size + 3, 0xFFAAAAAA);
        g.drawString(font, "W", mapX - 10, mapY + size / 2 - 4, 0xFFAAAAAA, false);
        g.drawString(font, "E", mapX + size + 4, mapY + size / 2 - 4, 0xFFAAAAAA, false);

        int targetIndex = 0;
        for (ContractTrackerStatePacket.TargetEntry target : ContractClientState.getTrackerTargets()) {
            int targetX = toMapX(mapX, size, target.areaX());
            int targetY = toMapY(mapY, size, target.areaZ());
            int targetRadius = Math.max(5, Math.min(18,
                    Math.round(target.areaRadius() / (ContractClientState.getZoneRadius() * 2.0F) * size)));
            int fillColor = targetIndex % 2 == 0 ? 0x66FF2222 : 0x66FFD21F;
            int outlineColor = targetIndex % 2 == 0 ? 0xAAFF5555 : 0xAAFFFF55;
            drawFilledCircle(g, targetX, targetY, targetRadius, fillColor);
            drawCircleOutline(g, targetX, targetY, targetRadius, outlineColor);
            targetIndex++;
        }

        int playerX = toMapX(mapX, size, ContractClientState.getPlayerX());
        int playerY = toMapY(mapY, size, ContractClientState.getPlayerZ());
        renderPlayerDirectionMarker(g, playerX, playerY, mapX, mapY, size, partialTick);
    }

    private void renderPlayerDirectionMarker(
            GuiGraphics g,
            int playerX,
            int playerY,
            int mapX,
            int mapY,
            int size,
            float partialTick
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        float yaw = interpolateYaw(minecraft.player.yRotO, minecraft.player.getYRot(), partialTick);
        double directionX = directionX(yaw);
        double directionY = directionY(yaw);

        g.flush();
        g.enableScissor(mapX + 3, mapY + 3, mapX + size - 3, mapY + size - 3);
        try {
            renderDirectionRay(g, playerX, playerY, directionX, directionY);
            renderPlayerTriangle(g, playerX, playerY, directionX, directionY);
            g.flush();
        } finally {
            g.disableScissor();
        }
    }

    private void renderDirectionRay(GuiGraphics g, float centerX, float centerY, double directionX, double directionY) {
        float endX = centerX - (float) directionX * 5.0F;
        float endY = centerY - (float) directionY * 5.0F;
        float startX = endX - (float) directionX * 10.0F;
        float startY = endY - (float) directionY * 10.0F;

        drawLineQuad(g, startX, startY, endX, endY, 1.5F, 0xFFF0F5E8);
    }

    private void renderPlayerTriangle(
            GuiGraphics g,
            float centerX,
            float centerY,
            double directionX,
            double directionY
    ) {
        drawTriangle(g, centerX, centerY, directionX, directionY, 6.0D, 5.0D, 0xFF14200F);
        drawTriangle(g, centerX, centerY, directionX, directionY, 5.25D, 4.0D, 0xFFF0F5E8);
    }

    private void drawLineQuad(GuiGraphics g, float startX, float startY, float endX, float endY, float width, int color) {
        float deltaX = endX - startX;
        float deltaY = endY - startY;
        float length = Mth.sqrt(deltaX * deltaX + deltaY * deltaY);
        if (length <= Mth.EPSILON) return;

        float offsetX = -deltaY / length * width / 2.0F;
        float offsetY = deltaX / length * width / 2.0F;
        drawQuad(g,
                startX + offsetX, startY + offsetY,
                endX + offsetX, endY + offsetY,
                endX - offsetX, endY - offsetY,
                startX - offsetX, startY - offsetY,
                color);
    }

    private void drawTriangle(
            GuiGraphics g,
            float centerX,
            float centerY,
            double directionX,
            double directionY,
            double halfLength,
            double halfBaseWidth,
            int color
    ) {
        double tipX = centerX + directionX * halfLength;
        double tipY = centerY + directionY * halfLength;
        double baseX = centerX - directionX * halfLength;
        double baseY = centerY - directionY * halfLength;
        double perpendicularX = -directionY;
        double perpendicularY = directionX;
        double leftX = baseX + perpendicularX * halfBaseWidth;
        double leftY = baseY + perpendicularY * halfBaseWidth;
        double rightX = baseX - perpendicularX * halfBaseWidth;
        double rightY = baseY - perpendicularY * halfBaseWidth;

        g.flush();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f matrix = g.pose().last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder vertices = tesselator.getBuilder();
        vertices.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        int alpha = color >>> 24;
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        addVertex(vertices, matrix, (float) tipX, (float) tipY, red, green, blue, alpha);
        addVertex(vertices, matrix, (float) rightX, (float) rightY, red, green, blue, alpha);
        addVertex(vertices, matrix, (float) leftX, (float) leftY, red, green, blue, alpha);
        tesselator.end();
    }

    private void drawQuad(
            GuiGraphics g,
            float x1,
            float y1,
            float x2,
            float y2,
            float x3,
            float y3,
            float x4,
            float y4,
            int color
    ) {
        Matrix4f matrix = g.pose().last().pose();
        VertexConsumer vertices = g.bufferSource().getBuffer(RenderType.gui());
        int alpha = color >>> 24;
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;

        addVertex(vertices, matrix, x1, y1, red, green, blue, alpha);
        addVertex(vertices, matrix, x2, y2, red, green, blue, alpha);
        addVertex(vertices, matrix, x3, y3, red, green, blue, alpha);
        addVertex(vertices, matrix, x4, y4, red, green, blue, alpha);
    }

    private void addVertex(
            VertexConsumer vertices,
            Matrix4f matrix,
            float x,
            float y,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        vertices.vertex(matrix, x, y, 0.0F).color(red, green, blue, alpha).endVertex();
    }

    static float interpolateYaw(float previousYaw, float currentYaw, float partialTick) {
        return Mth.rotLerp(partialTick, previousYaw, currentYaw);
    }

    static double directionX(float yaw) {
        return -Math.sin(Math.toRadians(yaw));
    }

    static double directionY(float yaw) {
        return Math.cos(Math.toRadians(yaw));
    }

    private int toMapX(int mapX, int size, int worldX) {
        int minX = ContractClientState.getZoneCenterX() - ContractClientState.getZoneRadius();
        int maxX = ContractClientState.getZoneCenterX() + ContractClientState.getZoneRadius();
        return mapX + Math.round((worldX - minX) / (float) Math.max(1, maxX - minX) * size);
    }

    private int toMapY(int mapY, int size, int worldZ) {
        int minZ = ContractClientState.getZoneCenterZ() - ContractClientState.getZoneRadius();
        int maxZ = ContractClientState.getZoneCenterZ() + ContractClientState.getZoneRadius();
        return mapY + Math.round((worldZ - minZ) / (float) Math.max(1, maxZ - minZ) * size);
    }

    private void drawRectOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void drawFilledCircle(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private void drawCircleOutline(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int a = 0; a < 360; a += 8) {
            double rad = Math.toRadians(a);
            int x = cx + (int) Math.round(Math.cos(rad) * r);
            int y = cy + (int) Math.round(Math.sin(rad) * r);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private String formatTime(long ms) {
        long totalSec = Math.max(0L, (ms + 999L) / 1000L);
        long m = totalSec / 60L;
        long s = totalSec % 60L;
        return String.format("%02d:%02d", m, s);
    }

    private static class TrackerTargetButton extends Button {
        private final ContractSelectionStatePacket.TargetEntry target;

        private TrackerTargetButton(int x, int y, int w, int h, ContractSelectionStatePacket.TargetEntry target) {
            super(Button.builder(Component.literal(target.name()), button -> {}).bounds(x, y, w, h));
            this.target = target;
            this.active = TabletClientState.getCoins() >= target.price();
        }

        @Override
        public void onPress() {
            if (!this.active) return;
            PacketHandler.sendToServer(new ContractSelectTargetPacket(target.uuid()));
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hover = this.isMouseOver(mouseX, mouseY);
            int border = ContractClientState.difficultyColor(target.difficulty());
            int bg = hover && this.active ? 0x55334422 : 0x33000000;
            int color = this.active ? border : 0xFF555555;

            g.fill(getX(), getY(), getX() + width, getY() + height, bg);
            g.fill(getX(), getY(), getX() + width, getY() + 1, border);
            g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, border);

            String text = target.name() + " | " + target.price() + " -> " + target.reward()
                    + " | " + target.careerPercent() + "%";
            g.drawString(Minecraft.getInstance().font, text, getX() + 4, getY() + 4, color, false);
        }
    }
}
