package com.makar.tacticaltablet.tablet.client.ui.widget;

import com.makar.tacticaltablet.tablet.client.GuiTextureRenderer;
import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Compact tactical button for an existing icon texture or atlas region. */
public final class TacticalIconButton extends TacticalButton {
    private final IconRegion icon;
    private Integer normalTint;
    private Integer hoverTint;
    private Integer disabledTint;

    public TacticalIconButton(int x, int y, int size, Component narration, OnPress action, IconRegion icon) {
        super(x, y, Math.max(size, TacticalTheme.MIN_CLICK_TARGET), Math.max(size, TacticalTheme.MIN_CLICK_TARGET),
                narration, action);
        this.icon = Objects.requireNonNull(icon, "icon");
    }

    public TacticalIconButton withTint(int normal, int hover, int disabled) {
        this.normalTint = normal;
        this.hoverTint = hover;
        this.disabledTint = disabled;
        return this;
    }

    @Override
    public TacticalIconButton withFocusKey(String focusKey) {
        super.withFocusKey(focusKey);
        return this;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int resolvedNormal = normalTint == null ? TacticalUi.currentPalette().textSecondary() : normalTint;
        int resolvedHover = hoverTint == null ? TacticalUi.currentPalette().accent() : hoverTint;
        int resolvedDisabled = disabledTint == null ? TacticalUi.currentPalette().textDisabled() : disabledTint;
        int tint = !active ? resolvedDisabled
                : isMouseOver(mouseX, mouseY) || isFocused() || isSelectedState() ? resolvedHover : resolvedNormal;
        float red = (tint >> 16 & 0xFF) / 255.0F;
        float green = (tint >> 8 & 0xFF) / 255.0F;
        float blue = (tint & 0xFF) / 255.0F;
        float alpha = (tint >>> 24) / 255.0F;
        int iconX = getX() + (width - icon.displaySize()) / 2;
        int iconY = getY() + (height - icon.displaySize()) / 2;
        GuiTextureRenderer.blitRegionWithAlpha(graphics, icon.texture(), iconX, iconY,
                icon.displaySize(), icon.displaySize(), icon.u(), icon.v(), icon.regionWidth(), icon.regionHeight(),
                icon.textureWidth(), icon.textureHeight(), red, green, blue, alpha);
    }

    public record IconRegion(ResourceLocation texture, float u, float v, int regionWidth, int regionHeight,
                             int textureWidth, int textureHeight, int displaySize) {
        public IconRegion {
            Objects.requireNonNull(texture, "texture");
            if (regionWidth <= 0 || regionHeight <= 0 || textureWidth <= 0 || textureHeight <= 0 || displaySize <= 0) {
                throw new IllegalArgumentException("Icon dimensions must be positive");
            }
        }
    }
}
