package com.makar.tacticaltablet.tablet.client.ui.widget;

import com.makar.tacticaltablet.tablet.client.GuiTextureRenderer;
import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.render.ScissorScope;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/** EditBox-backed field that retains vanilla editing, selection, clipboard, and narration behavior. */
public final class TacticalTextField extends EditBox implements FocusKeyProvider {
    private static final int ICON_GAP = 4;
    private static final int CLEAR_BUTTON_WIDTH = 16;

    private TacticalIconButton.IconRegion leadingIcon;
    private boolean clearButton;
    private boolean error;
    private String focusKey = "";

    public TacticalTextField(Font font, int x, int y, int width, int height, Component narration) {
        super(font, x, y, width, Math.max(TacticalTheme.MIN_CLICK_TARGET, height), narration);
        setBordered(false);
    }

    public TacticalTextField withPlaceholder(Component placeholder) {
        setHint(placeholder);
        return this;
    }

    public TacticalTextField withLeadingIcon(TacticalIconButton.IconRegion leadingIcon) {
        this.leadingIcon = leadingIcon;
        return this;
    }

    public TacticalTextField withClearButton(boolean clearButton) {
        this.clearButton = clearButton;
        return this;
    }

    public TacticalTextField withFocusKey(String focusKey) {
        this.focusKey = Objects.requireNonNull(focusKey, "focusKey");
        return this;
    }

    @Override
    public String focusKey() {
        return focusKey;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public void setEnabled(boolean enabled) {
        this.active = enabled;
        setEditable(enabled);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (active && clearButton && !getValue().isEmpty() && button == 0 && isOverClearButton(mouseX, mouseY)) {
            setValue("");
            setFocused(true);
            return true;
        }
        int originalX = getX();
        int originalWidth = width;
        applyContentBounds(originalX, originalWidth);
        try {
            return super.mouseClicked(mouseX, mouseY, button);
        } finally {
            restoreBounds(originalX, originalWidth);
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var palette = TacticalUi.currentPalette();
        int border = !active ? palette.borderDisabled() : error ? palette.danger()
                : isFocused() ? palette.borderHover() : palette.border();
        int fill = active ? palette.surfaceRaised() : palette.surfaceDisabled();
        TacticalUi.drawCutCornerBorder(graphics, getX(), getY(), width, height, TacticalTheme.CORNER_CUT,
                TacticalTheme.BORDER_WIDTH, border, fill);

        if (leadingIcon != null) renderLeadingIcon(graphics);
        int originalX = getX();
        int originalWidth = width;
        applyContentBounds(originalX, originalWidth);
        try {
            if (width > 0 && height > 2) {
                try (ScissorScope ignored = ScissorScope.open(
                        graphics, getX(), getY() + 1, width, height - 2)) {
                    super.renderWidget(graphics, mouseX, mouseY, partialTick);
                }
            }
        } finally {
            restoreBounds(originalX, originalWidth);
        }
        if (clearButton && active && !getValue().isEmpty()) renderClearButton(graphics, mouseX, mouseY);
    }

    private void renderLeadingIcon(GuiGraphics graphics) {
        int iconX = getX() + TacticalTheme.SPACING_SMALL;
        int iconY = getY() + (height - leadingIcon.displaySize()) / 2;
        float tint = active ? 1.0F : 0.45F;
        GuiTextureRenderer.blitRegionWithAlpha(graphics, leadingIcon.texture(), iconX, iconY,
                leadingIcon.displaySize(), leadingIcon.displaySize(), leadingIcon.u(), leadingIcon.v(),
                leadingIcon.regionWidth(), leadingIcon.regionHeight(), leadingIcon.textureWidth(),
                leadingIcon.textureHeight(), tint, tint, tint, 1.0F);
    }

    private void renderClearButton(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hovered = isOverClearButton(mouseX, mouseY);
        int centerX = getX() + width - CLEAR_BUTTON_WIDTH / 2 - 2;
        int centerY = getY() + height / 2;
        int color = hovered ? TacticalUi.currentPalette().accent() : TacticalUi.currentPalette().textSecondary();
        for (int offset = -2; offset <= 2; offset++) {
            graphics.fill(centerX + offset, centerY + offset, centerX + offset + 1, centerY + offset + 1, color);
            graphics.fill(centerX + offset, centerY - offset, centerX + offset + 1, centerY - offset + 1, color);
        }
    }

    private boolean isOverClearButton(double mouseX, double mouseY) {
        return mouseX >= getX() + width - CLEAR_BUTTON_WIDTH && mouseX < getX() + width
                && mouseY >= getY() && mouseY < getY() + height;
    }

    private int leftInset() {
        return leadingIcon == null ? 0 : leadingIcon.displaySize() + ICON_GAP + TacticalTheme.SPACING_SMALL;
    }

    private int rightInset() {
        return clearButton ? CLEAR_BUTTON_WIDTH : 0;
    }

    private void applyContentBounds(int originalX, int originalWidth) {
        int left = leftInset();
        int right = rightInset();
        setX(originalX + left);
        width = Math.max(1, originalWidth - left - right);
    }

    private void restoreBounds(int originalX, int originalWidth) {
        setX(originalX);
        width = originalWidth;
    }
}
