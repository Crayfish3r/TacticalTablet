package com.makar.tacticaltablet.tablet.client.ui.widget;

import com.makar.tacticaltablet.tablet.client.ui.TacticalLayout;
import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.animation.AnimatedFloat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Objects;

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
    private final GuiEventListener previousFocus;
    private final AnimatedFloat appearance =
            new AnimatedFloat(0.0F, TacticalTheme.APPEAR_DURATION_SECONDS);
    private TacticalLayout.Rect bounds = new TacticalLayout.Rect(0, 0, 0, 0);
    private List<FormattedCharSequence> bodyLines = List.of();
    private TacticalButton confirmButton;
    private TacticalButton cancelButton;

    public TacticalDialog(Screen parent, Component title, Component body, Component confirmLabel,
                          Component cancelLabel, Runnable confirmAction, Runnable cancelAction,
                          boolean allowEscape, boolean danger) {
        super(Objects.requireNonNull(title, "title"));
        this.parent = Objects.requireNonNull(parent, "parent");
        this.body = Objects.requireNonNull(body, "body");
        this.confirmLabel = Objects.requireNonNull(confirmLabel, "confirmLabel");
        this.cancelLabel = Objects.requireNonNull(cancelLabel, "cancelLabel");
        this.confirmAction = Objects.requireNonNull(confirmAction, "confirmAction");
        this.cancelAction = Objects.requireNonNull(cancelAction, "cancelAction");
        this.allowEscape = allowEscape;
        this.danger = danger;
        this.previousFocus = parent.getFocused();
    }

    @Override
    protected void init() {
        clearWidgets();
        bounds = TacticalLayout.centeredPanel(width, height, PREFERRED_WIDTH, PREFERRED_HEIGHT);
        bodyLines = font.split(body, Math.max(1, bounds.width() - TacticalTheme.SPACING_LARGE * 2));
        int buttonWidth = Math.max(72, (bounds.width() - TacticalTheme.SPACING_LARGE * 3) / 2);
        int buttonY = bounds.bottom() - TacticalTheme.SPACING_LARGE - TacticalTheme.CONTROL_HEIGHT;
        cancelButton = TacticalButton.standard(bounds.x() + TacticalTheme.SPACING_LARGE, buttonY,
                buttonWidth, cancelLabel, ignored -> cancel());
        confirmButton = TacticalButton.standard(bounds.right() - TacticalTheme.SPACING_LARGE - buttonWidth,
                buttonY, buttonWidth, confirmLabel, ignored -> confirm());
        if (danger) confirmButton.withAccentColor(TacticalTheme.DANGER).withAccentBar(true);
        addRenderableWidget(cancelButton);
        addRenderableWidget(confirmButton);
        setInitialFocus(confirmButton);
        appearance.snapTo(0.0F);
        appearance.setTarget(1.0F);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        TacticalUi.beginFrame();
        parent.render(graphics, -1, -1, partialTick);
        graphics.fill(0, 0, width, height, TacticalTheme.BACKDROP);
        appearance.update(TacticalUi.frameDeltaSeconds());
        int offset = Math.round((1.0F - appearance.value()) * APPEAR_OFFSET);
        int panelY = bounds.y() + offset;
        cancelButton.setY(bounds.bottom() - TacticalTheme.SPACING_LARGE - TacticalTheme.CONTROL_HEIGHT + offset);
        confirmButton.setY(bounds.bottom() - TacticalTheme.SPACING_LARGE - TacticalTheme.CONTROL_HEIGHT + offset);

        TacticalUi.drawPanel(graphics, bounds.x(), panelY, bounds.width(), bounds.height());
        TacticalUi.drawAccentBar(graphics, bounds.x() + TacticalTheme.SPACING_LARGE, panelY + 12, 16,
                danger ? TacticalTheme.DANGER : TacticalTheme.ACCENT);
        graphics.drawString(font, title, bounds.x() + TacticalTheme.SPACING_LARGE + 7, panelY + 14,
                TacticalTheme.TEXT_PRIMARY, false);
        int lineY = panelY + 38;
        for (FormattedCharSequence line : bodyLines) {
            graphics.drawString(font, line, bounds.x() + TacticalTheme.SPACING_LARGE, lineY,
                    TacticalTheme.TEXT_SECONDARY, false);
            lineY += font.lineHeight + 2;
            if (lineY >= panelY + bounds.height() - TacticalTheme.CONTROL_HEIGHT - 20) break;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
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
        confirmAction.run();
        returnToParent();
    }

    private void cancel() {
        cancelAction.run();
        returnToParent();
    }

    private void returnToParent() {
        Minecraft.getInstance().setScreen(parent);
        if (previousFocus != null && parent.children().contains(previousFocus)) parent.setFocused(previousFocus);
    }
}
