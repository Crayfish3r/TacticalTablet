package com.makar.tacticaltablet.tablet.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;

public final class TabletActionCard {
    private TabletActionCard() {
    }

    public static void render(GuiGraphics graphics, int x, int y, int width, int height,
                              boolean hovered, ResourceLocation icon, boolean iconExists,
                              String title, Presentation presentation) {
        int background = presentation.active() ? (hovered ? 0xFF294032 : 0xFF1D2B22) : 0xFF18231C;
        int border = presentation.rarityColor();
        graphics.fill(x, y, x + width, y + height, background);
        graphics.fill(x, y, x + width, y + 1, border);
        graphics.fill(x, y + height - 1, x + width, y + height, border);
        graphics.fill(x, y, x + 2, y + height, border);
        graphics.fill(x + width - 1, y, x + width, y + height, border);

        if (iconExists) {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, icon);
            graphics.blit(icon, x + 6, y + 9, 0, 0, 16, 16, 16, 16);
        } else {
            renderFallbackIcon(graphics, x + 6, y + 9, border);
        }

        int titleColor = presentation.active() ? 0xFFE6F0E8 : 0xFF77867B;
        graphics.drawString(Minecraft.getInstance().font, title, x + 26, y + 6, titleColor, false);
        graphics.drawString(Minecraft.getInstance().font, presentation.status(), x + 26, y + 19,
                presentation.statusColor(), false);
        graphics.drawCenteredString(Minecraft.getInstance().font, presentation.marker(), x + 119, y + 12,
                presentation.statusColor());
    }

    private static void renderFallbackIcon(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 16, y + 16, 0xFF101713);
        graphics.fill(x + 1, y + 1, x + 15, y + 2, color);
        graphics.fill(x + 1, y + 14, x + 15, y + 15, color);
        graphics.fill(x + 1, y + 2, x + 2, y + 14, color);
        graphics.fill(x + 14, y + 2, x + 15, y + 14, color);
        graphics.drawCenteredString(Minecraft.getInstance().font, "T", x + 8, y + 4, 0xFFE6F0E8);
    }

    public record Presentation(boolean active, int rarityColor, String status, int statusColor, String marker) {
    }
}
