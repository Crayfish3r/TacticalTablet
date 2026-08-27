package com.makar.tacticaltablet.tablet.client.ui.widget;

import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.animation.AnimatedFloat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** A vanilla-compatible button with a fully programmatic tactical appearance. */
public class TacticalButton extends Button implements FocusKeyProvider {
    private static final Component ELLIPSIS = Component.literal("\u2026");

    private final OnPress action;
    private final AnimatedFloat hoverAnimation =
            new AnimatedFloat(0.0F, TacticalTheme.HOVER_DURATION_SECONDS);
    private final AnimatedFloat selectedAnimation =
            new AnimatedFloat(0.0F, TacticalTheme.HOVER_DURATION_SECONDS);
    private BooleanSupplier selected = () -> false;
    private boolean pressed;
    private boolean accentBar;
    private Integer accentColor;
    private Runnable hoverAction = () -> { };
    private boolean wasHovered;
    private Component cachedMessage = Component.empty();
    private Component cachedDisplayMessage = Component.empty();
    private int cachedTextWidth = -1;
    private String focusKey = "";

    public TacticalButton(int x, int y, int width, int height, Component message, OnPress action) {
        super(Button.builder(message, ignored -> { }).bounds(x, y, width,
                Math.max(TacticalTheme.MIN_CLICK_TARGET, height)));
        this.action = Objects.requireNonNull(action, "action");
    }

    public static TacticalButton standard(int x, int y, int width, Component message, OnPress action) {
        return new TacticalButton(x, y, width, TacticalTheme.CONTROL_HEIGHT, message, action);
    }

    public static TacticalButton compact(int x, int y, int width, Component message, OnPress action) {
        return new TacticalButton(x, y, width, TacticalTheme.CONTROL_HEIGHT_COMPACT, message, action);
    }

    public TacticalButton selectedWhen(BooleanSupplier selected) {
        this.selected = Objects.requireNonNull(selected, "selected");
        return this;
    }

    public TacticalButton withAccentBar(boolean accentBar) {
        this.accentBar = accentBar;
        return this;
    }

    public TacticalButton withAccentColor(int accentColor) {
        this.accentColor = accentColor;
        return this;
    }

    public TacticalButton withTooltip(Component tooltip) {
        setTooltip(tooltip == null ? null : Tooltip.create(tooltip));
        return this;
    }

    public TacticalButton onHover(Runnable hoverAction) {
        this.hoverAction = Objects.requireNonNull(hoverAction, "hoverAction");
        return this;
    }

    public TacticalButton withFocusKey(String focusKey) {
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
        if (hovered && !wasHovered && active) hoverAction.run();
        wasHovered = hovered;

        hoverAnimation.setTarget(hovered && active ? 1.0F : 0.0F);
        selectedAnimation.setTarget(isSelected && active ? 1.0F : 0.0F);
        if (TacticalUi.currentFrame().reducedMotion()) {
            hoverAnimation.snapTo(hovered && active ? 1.0F : 0.0F);
            selectedAnimation.snapTo(isSelected && active ? 1.0F : 0.0F);
        } else {
            float delta = TacticalUi.currentFrame().deltaSeconds();
            hoverAnimation.update(delta);
            selectedAnimation.update(delta);
        }

        TacticalUi.ControlVisualState state = resolveState(hovered, isSelected);
        int resolvedAccent = accentColor == null ? TacticalUi.currentPalette().accent() : accentColor;
        TacticalUi.drawButton(graphics, getX(), getY(), width, height, state,
                hoverAnimation.value(), selectedAnimation.value(), resolvedAccent);
        if (accentBar && active) {
            TacticalUi.drawAccentBar(graphics, getX() + 2, getY() + 4, Math.max(0, height - 8), resolvedAccent);
        }
        renderContent(graphics, mouseX, mouseY, partialTick);
    }

    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int availableWidth = Math.max(0, width - TacticalTheme.SPACING_LARGE * 2 - (accentBar ? 3 : 0));
        Component displayMessage = displayMessage(font, availableWidth);
        int color = active ? TacticalUi.currentPalette().textPrimary() : TacticalUi.currentPalette().textDisabled();
        graphics.drawCenteredString(font, displayMessage, getX() + width / 2,
                getY() + (height - font.lineHeight) / 2 + 1, color);
    }

    protected final int accentColor() {
        return accentColor;
    }

    protected final boolean isSelectedState() {
        return selected.getAsBoolean();
    }

    private TacticalUi.ControlVisualState resolveState(boolean hovered, boolean isSelected) {
        return new TacticalUi.ControlVisualState(
                active,
                hovered,
                isFocused(),
                pressed && (hovered || isFocused()),
                isSelected
        );
    }

    private Component displayMessage(Font font, int availableWidth) {
        Component message = getMessage();
        if (message != cachedMessage || availableWidth != cachedTextWidth) {
            cachedMessage = message;
            cachedTextWidth = availableWidth;
            if (font.width(message) <= availableWidth) {
                cachedDisplayMessage = message;
            } else {
                int ellipsisWidth = font.width(ELLIPSIS);
                String clipped = font.plainSubstrByWidth(message.getString(), Math.max(0, availableWidth - ellipsisWidth));
                cachedDisplayMessage = Component.literal(clipped).append(ELLIPSIS);
            }
        }
        return cachedDisplayMessage;
    }
}
