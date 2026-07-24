package com.makar.tacticaltablet.tablet.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public final class GuiTextureRenderer {
    private GuiTextureRenderer() {
    }

    public static void blitWithAlpha(
            GuiGraphics graphics,
            ButtonTextureSpec texture,
            int x,
            int y,
            int logicalWidth,
            int logicalHeight
    ) {
        blitWithAlpha(
                graphics,
                texture.location(),
                x,
                y,
                logicalWidth,
                logicalHeight,
                texture.width(),
                texture.height()
        );
    }

    public static void blitWithAlpha(
            GuiGraphics graphics,
            ButtonTextureSpec texture,
            int x,
            int y,
            int logicalWidth,
            int logicalHeight,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        blitWithAlpha(
                graphics,
                texture.location(),
                x,
                y,
                logicalWidth,
                logicalHeight,
                texture.width(),
                texture.height(),
                red,
                green,
                blue,
                alpha
        );
    }

    public static void blitWithAlpha(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int logicalWidth,
            int logicalHeight,
            int textureWidth,
            int textureHeight
    ) {
        blitWithAlpha(
                graphics,
                texture,
                x,
                y,
                logicalWidth,
                logicalHeight,
                textureWidth,
                textureHeight,
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );
    }

    public static void blitWithAlpha(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int logicalWidth,
            int logicalHeight,
            int textureWidth,
            int textureHeight,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        Objects.requireNonNull(graphics, "graphics");
        Objects.requireNonNull(texture, "texture");

        RenderSystem.enableBlend();
        try {
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, texture);
            graphics.setColor(red, green, blue, alpha);
            graphics.blit(
                    texture,
                    x,
                    y,
                    0,
                    0,
                    logicalWidth,
                    logicalHeight,
                    textureWidth,
                    textureHeight
            );
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
    }
}
