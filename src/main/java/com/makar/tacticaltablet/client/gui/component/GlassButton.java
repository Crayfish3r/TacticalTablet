package com.makar.tacticaltablet.client.gui.component;

import com.makar.tacticaltablet.client.gui.render.GuiRenderUtils;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.animation.AnimatedFloat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.Objects;

public final class GlassButton extends Button {

    private static final float HOVER_DURATION_SECONDS = 0.16F;
    private static final int CORNER_RADIUS = 5;
    private static final int SHADOW_COLOR = 0x30000000;
    private static final int DISABLED_BACKGROUND = 0x7823292E;
    private static final int DISABLED_BORDER = 0x5268757E;
    private static final int DISABLED_TEXT = 0xFF89939B;

    private final ButtonStyle style;
    private final AnimatedFloat hoverProgress = new AnimatedFloat(0.0F, HOVER_DURATION_SECONDS);

    public GlassButton(
            int x,
            int y,
            int width,
            int height,
            Component message,
            ButtonStyle style,
            OnPress onPress
    ) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.style = Objects.requireNonNull(style, "style");
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

        float progress = hoverProgress.value();
        int renderY = getY() - Math.round(progress);
        int background = active
                ? TacticalUi.lerpArgb(style.normalBackground, style.hoverBackground, progress)
                : DISABLED_BACKGROUND;
        int border = active
                ? TacticalUi.lerpArgb(style.normalBorder, style.hoverBorder, progress)
                : DISABLED_BORDER;
        int text = active
                ? TacticalUi.lerpArgb(style.normalText, style.hoverText, progress)
                : DISABLED_TEXT;
        int highlight = active
                ? TacticalUi.lerpArgb(style.normalHighlight, style.hoverHighlight, progress)
                : 0x10FFFFFF;

        GuiRenderUtils.fillRoundedRect(
                graphics,
                getX() + 1,
                renderY + 3,
                width - 2,
                height,
                CORNER_RADIUS,
                SHADOW_COLOR
        );
        GuiRenderUtils.drawRoundedBorder(
                graphics,
                getX(),
                renderY,
                width,
                height,
                CORNER_RADIUS,
                border,
                background
        );
        graphics.fill(
                getX() + CORNER_RADIUS,
                renderY + 1,
                getX() + width - CORNER_RADIUS,
                renderY + 2,
                highlight
        );

        Font font = Minecraft.getInstance().font;
        Component message = getMessage();
        int textX = getX() + (width - font.width(message)) / 2;
        int textY = renderY + (height - font.lineHeight) / 2 + 1;
        graphics.drawString(font, message, textX, textY, text, false);
    }

    public enum ButtonStyle {
        NORMAL(
                0x8220252A,
                0xB437414B,
                0x64AAB4BE,
                0xC4C6D0DA,
                0xFFE8EDF2,
                0xFFFFFFFF,
                0x14FFFFFF,
                0x32FFFFFF
        ),
        PLAY(
                0x961E7841,
                0xBE28A555,
                0xB450DC78,
                0xF060E38B,
                0xFFF0FFF5,
                0xFFFFFFFF,
                0x1EFFFFFF,
                0x42FFFFFF
        ),
        DANGER(
                0x96782323,
                0xBEAA2D2D,
                0xB4E65555,
                0xF0F06A6A,
                0xFFFFEEEE,
                0xFFFFFFFF,
                0x18FFFFFF,
                0x38FFFFFF
        );

        private final int normalBackground;
        private final int hoverBackground;
        private final int normalBorder;
        private final int hoverBorder;
        private final int normalText;
        private final int hoverText;
        private final int normalHighlight;
        private final int hoverHighlight;

        ButtonStyle(
                int normalBackground,
                int hoverBackground,
                int normalBorder,
                int hoverBorder,
                int normalText,
                int hoverText,
                int normalHighlight,
                int hoverHighlight
        ) {
            this.normalBackground = normalBackground;
            this.hoverBackground = hoverBackground;
            this.normalBorder = normalBorder;
            this.hoverBorder = hoverBorder;
            this.normalText = normalText;
            this.hoverText = hoverText;
            this.normalHighlight = normalHighlight;
            this.hoverHighlight = hoverHighlight;
        }
    }
}
