package com.makar.tacticaltablet.tablet.client.ui.widget;

import com.makar.tacticaltablet.tablet.client.GuiTextureRenderer;
import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.animation.AnimatedFloat;
import com.makar.tacticaltablet.tablet.client.ui.render.ScissorScope;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Accessible, clickable card whose visual animation never changes its hitbox. */
public final class TacticalCard extends Button implements FocusKeyProvider {
    private final Component title;
    private final Component subtitle;
    private final OnPress action;
    private final AnimatedFloat emphasis =
            new AnimatedFloat(0.0F, TacticalTheme.HOVER_DURATION_SECONDS);
    private BooleanSupplier selected = () -> false;
    private TacticalIconButton.IconRegion icon;
    private Integer accentColor;
    private boolean pressed;
    private String focusKey = "";

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

    public TacticalCard withFocusKey(String focusKey) {
        this.focusKey = Objects.requireNonNull(focusKey, "focusKey");
        return this;
    }

    @Override
    public String focusKey() {
        return focusKey;
    }

    @Override
    public void onPress() {
        if (active) action.onPress(this);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        pressed = handled && button == 0;
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        pressed = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        if (handled && active) pressed = true;
        return handled;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        boolean handled = super.keyReleased(keyCode, scanCode, modifiers);
        pressed = false;
        return handled;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isMouseOver(mouseX, mouseY);
        boolean isSelected = selected.getAsBoolean();
        emphasis.setTarget(active && (hovered || isSelected || isFocused()) ? 1.0F : 0.0F);
        if (TacticalUi.currentFrame().reducedMotion()) {
            emphasis.snapTo(active && (hovered || isSelected || isFocused()) ? 1.0F : 0.0F);
        } else {
            emphasis.update(TacticalUi.currentFrame().deltaSeconds());
        }

        TacticalUi.ControlVisualState state = new TacticalUi.ControlVisualState(
                active, hovered, isFocused(), pressed && (hovered || isFocused()), isSelected);
        int visualY = getY() - (hovered && active ? Math.round(emphasis.value()) : 0);
        int resolvedAccent = accentColor == null ? TacticalUi.currentPalette().accent() : accentColor;
        TacticalUi.drawCard(graphics, getX(), visualY, width, height, state, resolvedAccent, emphasis.value());
        TacticalUi.drawAccentBar(graphics, getX() + 2, visualY + 4, Math.max(0, height - 8), resolvedAccent);

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
        try (ScissorScope ignored = ScissorScope.open(
                graphics, contentX, visualY + 1, textWidth, Math.max(1, height - 2))) {
            graphics.drawString(font, title, textX, titleY,
                    active ? TacticalUi.currentPalette().textPrimary() : TacticalUi.currentPalette().textDisabled(), false);
            graphics.drawString(font, subtitle, textX, titleY + font.lineHeight + 2,
                    active ? TacticalUi.currentPalette().textSecondary() : TacticalUi.currentPalette().textDisabled(), false);
        }
    }
}
