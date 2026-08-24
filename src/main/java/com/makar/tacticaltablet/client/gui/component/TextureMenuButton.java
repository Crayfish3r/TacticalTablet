package com.makar.tacticaltablet.client.gui.component;

import com.makar.tacticaltablet.client.gui.MenuTextureSet;
import com.makar.tacticaltablet.tablet.client.GuiTextureRenderer;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.animation.AnimatedFloat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public final class TextureMenuButton extends Button {

    private static final float HOVER_DURATION_SECONDS = 0.14F;

    private final ResourceLocation texture;
    private final AnimatedFloat hoverProgress = new AnimatedFloat(0.0F, HOVER_DURATION_SECONDS);
    private float revealProgress = 1.0F;
    private int revealOffsetY;

    public TextureMenuButton(
            int x,
            int y,
            int width,
            int height,
            ResourceLocation texture,
            Component narration,
            OnPress onPress
    ) {
        super(x, y, width, height, narration, onPress, DEFAULT_NARRATION);
        this.texture = Objects.requireNonNull(texture, "texture");
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean emphasized = active && isHoveredOrFocused();
        hoverProgress.setTarget(emphasized ? 1.0F : 0.0F);
        if (TacticalUi.currentFrame().reducedMotion()) {
            hoverProgress.snapTo(emphasized ? 1.0F : 0.0F);
        } else {
            hoverProgress.update(TacticalUi.currentFrame().deltaSeconds());
        }

        float hover = hoverProgress.value();
        float reveal = Math.max(0.0F, Math.min(1.0F, revealProgress));
        int expandX = Math.round(hover * 2.0F);
        int renderX = getX() - expandX;
        int renderY = getY()
                + Math.round((1.0F - reveal) * revealOffsetY)
                - Math.round(hover);
        int renderWidth = width + expandX * 2;
        int renderHeight = height + Math.round(hover);
        float brightness = 1.0F + hover * 0.10F;

        GuiTextureRenderer.blitRegionWithAlpha(
                graphics,
                texture,
                renderX,
                renderY,
                renderWidth,
                renderHeight,
                0.0F,
                0.0F,
                MenuTextureSet.BUTTON_WIDTH,
                MenuTextureSet.BUTTON_HEIGHT,
                MenuTextureSet.BUTTON_WIDTH,
                MenuTextureSet.BUTTON_HEIGHT,
                brightness,
                brightness,
                brightness,
                reveal
        );
    }

    public void setRevealProgress(float progress, int offsetY) {
        revealProgress = Math.max(0.0F, Math.min(1.0F, progress));
        revealOffsetY = Math.max(0, offsetY);
        active = revealProgress >= 0.65F;
    }
}
