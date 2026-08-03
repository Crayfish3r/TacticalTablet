package com.makar.tacticaltablet.tablet.client.ui.widget;

import com.makar.tacticaltablet.tablet.client.ui.TacticalLayout;
import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameClock;
import com.makar.tacticaltablet.tablet.client.ui.animation.AnimatedFloat;
import com.makar.tacticaltablet.tablet.client.ui.render.ScissorScope;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Reusable modal screen with confirm/cancel actions and a non-interactive parent backdrop. */
public class TacticalDialog extends Screen {
    private static final int PREFERRED_WIDTH = 260;
    private static final int PREFERRED_HEIGHT = 136;
    private static final int APPEAR_OFFSET = 8;

    private final Screen parent;
    private final Component body;
    private final Component confirmLabel;
    private final Component cancelLabel;
    private final Runnable confirmAction;
    private final Runnable cancelAction;
    private final boolean allowEscape;
    private final boolean danger;
    private final BooleanSupplier confirmEnabled;
    private final FocusTarget previousFocus;
    private final AnimatedFloat appearance =
            new AnimatedFloat(0.0F, TacticalTheme.APPEAR_DURATION_SECONDS);
    private final UiFrameClock frameClock = new UiFrameClock();
    private TacticalLayout.Rect bounds = new TacticalLayout.Rect(0, 0, 0, 0);
    private TacticalLayout.Rect bodyViewport = new TacticalLayout.Rect(0, 0, 0, 0);
    private List<FormattedCharSequence> bodyLines = List.of();
    private int bodyScrollLine;
    private TacticalButton confirmButton;
    private TacticalButton cancelButton;

    public TacticalDialog(Screen parent, Component title, Component body, Component confirmLabel,
                          Component cancelLabel, Runnable confirmAction, Runnable cancelAction,
                          boolean allowEscape, boolean danger) {
        this(parent, title, body, confirmLabel, cancelLabel, confirmAction, cancelAction,
                allowEscape, danger, () -> true);
    }

    public TacticalDialog(Screen parent, Component title, Component body, Component confirmLabel,
                          Component cancelLabel, Runnable confirmAction, Runnable cancelAction,
                          boolean allowEscape, boolean danger, BooleanSupplier confirmEnabled) {
        super(Objects.requireNonNull(title, "title"));
        this.parent = Objects.requireNonNull(parent, "parent");
        this.body = Objects.requireNonNull(body, "body");
        this.confirmLabel = Objects.requireNonNull(confirmLabel, "confirmLabel");
        this.cancelLabel = Objects.requireNonNull(cancelLabel, "cancelLabel");
        this.confirmAction = Objects.requireNonNull(confirmAction, "confirmAction");
        this.cancelAction = Objects.requireNonNull(cancelAction, "cancelAction");
        this.allowEscape = allowEscape;
        this.danger = danger;
        this.confirmEnabled = Objects.requireNonNull(confirmEnabled, "confirmEnabled");
        this.previousFocus = FocusTarget.capture(parent);
    }

    @Override
    protected void init() {
        clearWidgets();
        bounds = TacticalLayout.centeredPanel(width, height, PREFERRED_WIDTH, PREFERRED_HEIGHT);
        bodyLines = font.split(body, Math.max(1, bounds.width() - TacticalTheme.SPACING_LARGE * 2));
        bodyScrollLine = 0;
        int buttonWidth = Math.max(1, (bounds.width() - TacticalTheme.SPACING_LARGE * 3) / 2);
        int buttonY = bounds.bottom() - TacticalTheme.SPACING_LARGE - TacticalTheme.CONTROL_HEIGHT;
        cancelButton = TacticalButton.standard(bounds.x() + TacticalTheme.SPACING_LARGE, buttonY,
                buttonWidth, cancelLabel, ignored -> cancel()).withFocusKey("dialog.cancel");
        confirmButton = TacticalButton.standard(bounds.right() - TacticalTheme.SPACING_LARGE - buttonWidth,
                buttonY, buttonWidth, confirmLabel, ignored -> confirm()).withFocusKey("dialog.confirm");
        confirmButton.active = confirmEnabled.getAsBoolean();
        if (danger) confirmButton.withAccentColor(TacticalTheme.DANGER).withAccentBar(true);
        addRenderableWidget(cancelButton);
        addRenderableWidget(confirmButton);
        setInitialFocus(danger ? cancelButton : confirmButton);
        int bodyTop = bounds.y() + 36;
        int bodyBottom = Math.max(bodyTop, buttonY - TacticalTheme.SPACING);
        bodyViewport = new TacticalLayout.Rect(
                bounds.x() + TacticalTheme.SPACING_LARGE,
                bodyTop,
                Math.max(0, bounds.width() - TacticalTheme.SPACING_LARGE * 2),
                Math.max(0, bodyBottom - bodyTop));
        appearance.snapTo(0.0F);
        appearance.setTarget(1.0F);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try (TacticalUi.FrameScope ignored = TacticalUi.openFrame(
                frameClock.nextFrame(Util.getMillis(), false))) {
            renderFrame(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderFrame(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        confirmButton.active = confirmEnabled.getAsBoolean();
        parent.render(graphics, -1, -1, partialTick);
        graphics.fill(0, 0, width, height, TacticalTheme.BACKDROP);
        if (TacticalUi.currentFrame().reducedMotion()) appearance.snapTo(1.0F);
        else appearance.update(TacticalUi.currentFrame().deltaSeconds());
        int offset = Math.round((1.0F - appearance.value()) * APPEAR_OFFSET);
        int panelY = bounds.y() + offset;
        cancelButton.setY(bounds.bottom() - TacticalTheme.SPACING_LARGE - TacticalTheme.CONTROL_HEIGHT + offset);
        confirmButton.setY(bounds.bottom() - TacticalTheme.SPACING_LARGE - TacticalTheme.CONTROL_HEIGHT + offset);

        TacticalUi.drawPanel(graphics, bounds.x(), panelY, bounds.width(), bounds.height());
        TacticalUi.drawAccentBar(graphics, bounds.x() + TacticalTheme.SPACING_LARGE, panelY + 12, 16,
                danger ? TacticalTheme.DANGER : TacticalTheme.ACCENT);
        graphics.drawString(font, title, bounds.x() + TacticalTheme.SPACING_LARGE + 7, panelY + 14,
                TacticalTheme.TEXT_PRIMARY, false);
        if (bodyViewport.width() > 0 && bodyViewport.height() > 0) {
            int viewportY = bodyViewport.y() + offset;
            try (ScissorScope ignored = ScissorScope.open(
                    graphics, bodyViewport.x(), viewportY, bodyViewport.width(), bodyViewport.height())) {
                int lineY = viewportY;
                int lineStep = font.lineHeight + 2;
                int maxVisible = Math.max(1, (bodyViewport.height() + lineStep - 1) / lineStep);
                int end = Math.min(bodyLines.size(), bodyScrollLine + maxVisible);
                for (int index = bodyScrollLine; index < end; index++) {
                    graphics.drawString(font, bodyLines.get(index), bodyViewport.x(), lineY,
                            TacticalTheme.TEXT_SECONDARY, false);
                    lineY += lineStep;
                }
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (bodyViewport.width() <= 0 || bodyViewport.height() <= 0
                || mouseX < bodyViewport.x() || mouseX >= bodyViewport.right()
                || mouseY < bodyViewport.y() || mouseY >= bodyViewport.bottom()) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int lineStep = font.lineHeight + 2;
        int visibleLines = Math.max(1, bodyViewport.height() / lineStep);
        int maxScroll = Math.max(0, bodyLines.size() - visibleLines);
        int next = Math.max(0, Math.min(maxScroll, bodyScrollLine - (int) Math.signum(delta)));
        if (next == bodyScrollLine) return false;
        bodyScrollLine = next;
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return allowEscape;
    }

    @Override
    public void onClose() {
        if (allowEscape) cancel();
    }

    private void confirm() {
        runAction(confirmAction);
    }

    private void cancel() {
        runAction(cancelAction);
    }

    private void runAction(Runnable action) {
        action.run();
        if (Minecraft.getInstance().screen == this) returnToParent();
    }

    private void returnToParent() {
        Minecraft.getInstance().setScreen(parent);
        GuiEventListener restored = previousFocus.resolve(parent);
        if (restored != null) parent.setFocused(restored);
    }

    private record FocusTarget(String key, int fallbackIndex) {
        private static FocusTarget capture(Screen screen) {
            GuiEventListener focused = screen.getFocused();
            if (focused == null) return new FocusTarget("", -1);
            String key = focused instanceof FocusKeyProvider provider ? provider.focusKey() : "";
            return new FocusTarget(key == null ? "" : key, screen.children().indexOf(focused));
        }

        private GuiEventListener resolve(Screen screen) {
            if (!key.isBlank()) {
                for (GuiEventListener child : screen.children()) {
                    if (child instanceof FocusKeyProvider provider && key.equals(provider.focusKey())) return child;
                }
            }
            return fallbackIndex >= 0 && fallbackIndex < screen.children().size()
                    ? screen.children().get(fallbackIndex)
                    : null;
        }
    }
}
