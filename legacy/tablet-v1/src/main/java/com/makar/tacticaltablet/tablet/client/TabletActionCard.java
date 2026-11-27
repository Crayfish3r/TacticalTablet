package com.makar.tacticaltablet.tablet.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import com.makar.tacticaltablet.progression.ClassTier;

public final class TabletActionCard {
    private TabletActionCard() {
    }

    public static void render(GuiGraphics graphics, int x, int y, int width, int height,
                              boolean hovered, ResourceLocation icon, boolean iconExists,
                              String title, Presentation presentation) {
        ButtonTextureSpec texture = TabletButtonTextures.CLASS_BUTTON;
        ClassButtonStyle.Tint tint = ClassButtonStyle.tint(
                presentation.tier(),
                presentation.active(),
                hovered
        );
        GuiTextureRenderer.blitWithAlpha(
                graphics,
                texture,
                x,
                y,
                width,
                height,
                tint.red(),
                tint.green(),
                tint.blue(),
                tint.alpha()
        );

        if (iconExists) {
            GuiTextureRenderer.blitWithAlpha(graphics, icon, x + 6, y + 9, 16, 16, 16, 16);
        } else {
            renderFallbackIcon(graphics, x + 6, y + 9, ClassButtonStyle.color(presentation.tier()));
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

    public record Presentation(boolean active, ClassTier tier, String status, int statusColor, String marker) {
    }
}
