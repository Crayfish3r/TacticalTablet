package com.makar.tacticaltablet.tablet.client.ui.widget;

import com.makar.tacticaltablet.tablet.client.GuiTextureRenderer;
import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.animation.AnimatedFloat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Accessible, clickable card whose visual animation never changes its hitbox. */
public final class TacticalCard extends Button {
    private final Component title;
    private final Component subtitle;
    private final OnPress action;
    private final AnimatedFloat emphasis =
            new AnimatedFloat(0.0F, TacticalTheme.HOVER_DURATION_SECONDS);
    private BooleanSupplier selected = () -> false;
    private TacticalIconButton.IconRegion icon;
    private int accentColor = TacticalTheme.ACCENT;

    public TacticalCard(int x, int y, int width, int height, Component title, Component subtitle, OnPress action) {
        super(Button.builder(Objects.requireNonNull(title, "title"), ignored -> { }).bounds(x, y, width, height));
        this.title = title;
        this.subtitle = Objects.requireNonNull(subtitle, "subtitle");
        this.action = Objects.requireNonNull(action, "action");
    }

    public TacticalCard selectedWhen(BooleanSupplier selected) {
        this.selected = Objects.requireNonNull(selected, "selected");
        return this;
    }

    public TacticalCard withAccentColor(int accentColor) {
        this.accentColor = accentColor;
        return this;
    }

    public TacticalCard withIcon(TacticalIconButton.IconRegion icon) {
        this.icon = icon;
        return this;
    }

    @Override
    public void onPress() {
        if (active) action.onPress(this);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isMouseOver(mouseX, mouseY);
        boolean isSelected = selected.getAsBoolean();
        emphasis.setTarget(active && (hovered || isSelected || isFocused()) ? 1.0F : 0.0F);
        emphasis.update(TacticalUi.frameDeltaSeconds());

        TacticalUi.ControlState state = !active ? TacticalUi.ControlState.DISABLED
                : isSelected ? TacticalUi.ControlState.SELECTED
                : isFocused() ? TacticalUi.ControlState.FOCUSED
                : hovered ? TacticalUi.ControlState.HOVERED : TacticalUi.ControlState.NORMAL;
        int visualY = getY() - (hovered && active ? Math.round(emphasis.value()) : 0);
        TacticalUi.drawCard(graphics, getX(), visualY, width, height, state, accentColor, emphasis.value());
        TacticalUi.drawAccentBar(graphics, getX() + 2, visualY + 4, Math.max(0, height - 8), accentColor);

        int contentX = getX() + TacticalTheme.SPACING_LARGE;
        if (icon != null) {
            int iconY = visualY + (height - icon.displaySize()) / 2;
            GuiTextureRenderer.blitRegionWithAlpha(graphics, icon.texture(), contentX, iconY,
                    icon.displaySize(), icon.displaySize(), icon.u(), icon.v(), icon.regionWidth(), icon.regionHeight(),
                    icon.textureWidth(), icon.textureHeight(), 1.0F, 1.0F, 1.0F, active ? 1.0F : 0.45F);
            contentX += icon.displaySize() + TacticalTheme.SPACING;
        }

        Font font = Minecraft.getInstance().font;
        int textWidth = Math.max(0, getX() + width - TacticalTheme.SPACING - contentX);
        int textX = contentX;
        int titleY = visualY + Math.max(4, (height - font.lineHeight * 2 - 2) / 2);
        if (textWidth <= 0 || height <= 2) return;
        graphics.enableScissor(contentX, visualY + 1, contentX + textWidth, visualY + height - 1);
        try {
            graphics.drawString(font, title, textX, titleY,
                    active ? TacticalTheme.TEXT_PRIMARY : TacticalTheme.TEXT_DISABLED, false);
            graphics.drawString(font, subtitle, textX, titleY + font.lineHeight + 2,
                    active ? TacticalTheme.TEXT_SECONDARY : TacticalTheme.TEXT_DISABLED, false);
        } finally {
            graphics.disableScissor();
        }
    }
}
