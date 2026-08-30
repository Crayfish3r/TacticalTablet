package com.makar.tacticaltablet.client.gui;

import com.makar.tacticaltablet.client.ExternalUiTheme;
import com.makar.tacticaltablet.core.TacticalTabletClientConfig;
import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameClock;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameContext;
import com.makar.tacticaltablet.tablet.client.ui.UiPalette;
import com.makar.tacticaltablet.tablet.client.ui.UiPaletteProvider;
import com.makar.tacticaltablet.tablet.client.ui.widget.TacticalButton;
import com.makar.tacticaltablet.tablet.client.ui.widget.TacticalTextField;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class JoinServerConfigScreen extends Screen implements UiPaletteProvider {

    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 174;
    private static final int PANEL_MARGIN = 10;
    private static final int CONTENT_WIDTH = 360;
    private static final int BUTTON_GAP = 6;

    private final Screen parent;
    private final UiFrameClock frameClock = new UiFrameClock();
    private TacticalTextField addressField;
    private Layout layout;
    private Component errorMessage = Component.empty();

    public JoinServerConfigScreen(Screen parent) {
        super(Component.translatable("screen.tacticaltablet.server_config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout = Layout.calculate(width, height);
        addressField = addRenderableWidget(new TacticalTextField(
                font,
                layout.contentX(),
                layout.fieldY(),
                layout.contentWidth(),
                TacticalTheme.CONTROL_HEIGHT,
                Component.translatable("screen.tacticaltablet.server_config.address")
        ).withPlaceholder(Component.translatable("screen.tacticaltablet.server_config.address_hint"))
                .withClearButton(true));
        addressField.setMaxLength(JoinServerAddressPolicy.MAX_LENGTH);
        addressField.setValue(TacticalTabletClientConfig.getJoinServerAddress());
        addressField.setFocused(true);

        int buttonWidth = (layout.contentWidth() - BUTTON_GAP * 2) / 3;
        int buttonX = layout.contentX();
        addRenderableWidget(TacticalButton.standard(
                buttonX,
                layout.buttonY(),
                buttonWidth,
                Component.translatable("screen.tacticaltablet.server_config.save"),
                ignored -> saveAddress()
        ).withAccentBar(true));
        buttonX += buttonWidth + BUTTON_GAP;
        addRenderableWidget(TacticalButton.standard(
                buttonX,
                layout.buttonY(),
                buttonWidth,
                Component.translatable("screen.tacticaltablet.server_config.reset"),
                ignored -> resetToDefault()
        ));
        buttonX += buttonWidth + BUTTON_GAP;
        addRenderableWidget(TacticalButton.standard(
                buttonX,
                layout.buttonY(),
                layout.contentX() + layout.contentWidth() - buttonX,
                Component.translatable("screen.tacticaltablet.server_config.cancel"),
                ignored -> onClose()
        ));
    }

    private void saveAddress() {
        JoinServerAddressPolicy.normalize(addressField.getValue()).ifPresentOrElse(address -> {
            TacticalTabletClientConfig.setJoinServerAddress(address);
            Minecraft.getInstance().setScreen(parent);
        }, () -> {
            addressField.setError(true);
            errorMessage = Component.translatable("screen.tacticaltablet.server_config.invalid");
        });
    }

    private void resetToDefault() {
        addressField.setValue(TacticalTabletClientConfig.DEFAULT_JOIN_SERVER_ADDRESS);
        addressField.setError(false);
        errorMessage = Component.empty();
        addressField.setFocused(true);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiFrameContext frame = frameClock.nextFrame(Util.getMillis(), reducedMotion());
        try (TacticalUi.FrameScope ignored = TacticalUi.openFrame(frame, ExternalUiTheme.PALETTE)) {
            TacticalScreenBackground.render(graphics, minecraft, width, height);
            TacticalUi.drawPanel(graphics, layout.panelX(), layout.panelY(),
                    layout.panelWidth(), layout.panelHeight());
            graphics.drawCenteredString(font, title, width / 2, layout.titleY(),
                    TacticalUi.currentPalette().textPrimary());
            TacticalUi.drawDivider(graphics, layout.contentX(), layout.dividerY(),
                    layout.contentWidth(), false);
            graphics.drawString(font,
                    Component.translatable("screen.tacticaltablet.server_config.address"),
                    layout.contentX(), layout.labelY(), TacticalUi.currentPalette().textSecondary(), false);
            if (!errorMessage.getString().isEmpty()) {
                graphics.drawString(font, errorMessage, layout.contentX(), layout.errorY(),
                        TacticalUi.currentPalette().danger(), false);
            }
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private boolean reducedMotion() {
        return minecraft != null && minecraft.options.screenEffectScale().get() <= 0.0D;
    }

    @Override
    public UiPalette uiPalette() {
        return ExternalUiTheme.PALETTE;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight,
                          int contentX, int contentWidth, int titleY, int dividerY,
                          int labelY, int fieldY, int errorY, int buttonY) {
        private static Layout calculate(int screenWidth, int screenHeight) {
            int panelWidth = Math.min(PANEL_WIDTH, Math.max(1, screenWidth - PANEL_MARGIN * 2));
            int panelHeight = Math.min(PANEL_HEIGHT, Math.max(1, screenHeight - PANEL_MARGIN * 2));
            int panelX = (screenWidth - panelWidth) / 2;
            int panelY = (screenHeight - panelHeight) / 2;
            int contentWidth = Math.min(CONTENT_WIDTH, Math.max(1, panelWidth - 24));
            int contentX = panelX + (panelWidth - contentWidth) / 2;
            return new Layout(panelX, panelY, panelWidth, panelHeight, contentX, contentWidth,
                    panelY + 11, panelY + 29, panelY + 42, panelY + 56,
                    panelY + 84, panelY + 113);
        }
    }
}
