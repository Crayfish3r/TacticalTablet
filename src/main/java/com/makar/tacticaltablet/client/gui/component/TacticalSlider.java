package com.makar.tacticaltablet.client.gui.component;

import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.animation.AnimatedFloat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

public final class TacticalSlider extends AbstractSliderButton {

    private final DoubleFunction<Component> messageFactory;
    private final DoubleConsumer valueConsumer;
    private final AnimatedFloat hoverAnimation =
            new AnimatedFloat(0.0F, TacticalTheme.HOVER_DURATION_SECONDS);

    public TacticalSlider(int x, int y, int width, int height, double value,
                          DoubleFunction<Component> messageFactory,
                          DoubleConsumer valueConsumer) {
        super(x, y, width, Math.max(TacticalTheme.MIN_CLICK_TARGET, height),
                Component.empty(), clamp(value));
        this.messageFactory = Objects.requireNonNull(messageFactory, "messageFactory");
        this.valueConsumer = Objects.requireNonNull(valueConsumer, "valueConsumer");
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        if (messageFactory != null) setMessage(messageFactory.apply(value));
    }

    @Override
    protected void applyValue() {
        valueConsumer.accept(value);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isMouseOver(mouseX, mouseY);
        hoverAnimation.setTarget(hovered && active ? 1.0F : 0.0F);
        if (TacticalUi.currentFrame().reducedMotion()) {
            hoverAnimation.snapTo(hovered && active ? 1.0F : 0.0F);
        } else {
            hoverAnimation.update(TacticalUi.currentFrame().deltaSeconds());
        }

        TacticalUi.ControlVisualState state = new TacticalUi.ControlVisualState(
                active, hovered, isFocused(), false, false
        );
        TacticalUi.drawButton(graphics, getX(), getY(), width, height, state,
                hoverAnimation.value(), 0.0F, TacticalUi.currentPalette().accent());

        int trackX = getX() + TacticalTheme.SPACING_LARGE;
        int trackWidth = Math.max(1, width - TacticalTheme.SPACING_LARGE * 2);
        int trackY = getY() + height - 5;
        TacticalUi.drawProgressBar(graphics, trackX, trackY, trackWidth, 2,
                (float) value, TacticalUi.currentPalette().accent());

        int color = active ? TacticalUi.currentPalette().textPrimary() : TacticalUi.currentPalette().textDisabled();
        graphics.drawCenteredString(
                Minecraft.getInstance().font,
                getMessage(),
                getX() + width / 2,
                getY() + 4,
                color
        );
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) return 0.0D;
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
