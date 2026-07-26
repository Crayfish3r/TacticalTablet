package com.makar.tacticaltablet.tablet.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public final class GuiTextureRenderer {
    private static final ThreadLocal<Deque<BlendState>> BLEND_STATES =
            ThreadLocal.withInitial(ArrayDeque::new);

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

        withAlphaBlend(graphics, () -> {
            graphics.setColor(red, green, blue, alpha);
            try {
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
            }
        });
    }

    public static void withAlphaBlend(GuiGraphics graphics, Runnable drawCall) {
        Objects.requireNonNull(drawCall, "drawCall");
        beginAlphaBlend(graphics);
        try {
            drawCall.run();
        } finally {
            endAlphaBlend(graphics);
        }
    }

    public static void beginAlphaBlend(GuiGraphics graphics) {
        Objects.requireNonNull(graphics, "graphics");
        RenderSystem.assertOnRenderThread();
        BLEND_STATES.get().push(BlendState.capture());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void endAlphaBlend(GuiGraphics graphics) {
        Objects.requireNonNull(graphics, "graphics");
        RenderSystem.assertOnRenderThread();
        Deque<BlendState> states = BLEND_STATES.get();
        if (states.isEmpty()) {
            throw new IllegalStateException("Unbalanced alpha blend scope");
        }

        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        BlendState previous = states.pop();
        previous.restore();
        if (states.isEmpty()) {
            BLEND_STATES.remove();
        }
    }

    private record BlendState(
            boolean enabled,
            int sourceRgb,
            int destinationRgb,
            int sourceAlpha,
            int destinationAlpha
    ) {
        private static BlendState capture() {
            return new BlendState(
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
            );
        }

        private void restore() {
            RenderSystem.blendFuncSeparate(
                    sourceRgb,
                    destinationRgb,
                    sourceAlpha,
                    destinationAlpha
            );
            if (enabled) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
        }
    }
}
