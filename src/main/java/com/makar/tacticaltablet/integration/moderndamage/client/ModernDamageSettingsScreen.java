package com.makar.tacticaltablet.integration.moderndamage.client;

import com.makar.tacticaltablet.client.gui.TacticalScreenBackground;
import com.makar.tacticaltablet.core.TacticalTabletClientConfig;
import com.makar.tacticaltablet.integration.moderndamage.ModernDamageIntegration;
import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameClock;
import com.makar.tacticaltablet.tablet.client.ui.widget.TacticalButton;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ModernDamageSettingsScreen extends Screen implements com.makar.tacticaltablet.tablet.client.ui.UiPaletteProvider {
    private static final int PANEL_WIDTH = 600;
    private static final int PANEL_HEIGHT = 300;
    private final Screen parent;
    private final UiFrameClock frameClock = new UiFrameClock();
    private Layout layout;

    public ModernDamageSettingsScreen(Screen parent) {
        super(Component.translatable("screen.tacticaltablet.mdc.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout = Layout.calculate(width, height);
        int x = layout.contentX();
        int y = layout.contentY();
        int full = layout.contentWidth();
        TacticalButton editor = TacticalButton.standard(x, y, full,
                Component.translatable("screen.tacticaltablet.mdc.hud_editor"),
                ignored -> Minecraft.getInstance().setScreen(new ModernDamageHudEditorScreen(this)))
                .withAccentBar(true);
        editor.withTooltip(Component.translatable("screen.tacticaltablet.mdc.hud_enabled.desc"));
        addRenderableWidget(editor);

        y += 34;
        TacticalButton balance = TacticalButton.standard(x, y, full,
                Component.translatable("screen.tacticaltablet.mdc.server_balance"),
                ignored -> Minecraft.getInstance().setScreen(new ModernDamageBalanceScreen(this)))
                .withAccentBar(true);
        balance.active = minecraft != null && minecraft.getConnection() != null;
        if (!balance.active) balance.withTooltip(Component.translatable("screen.tacticaltablet.mdc.server_required"));
        addRenderableWidget(balance);

        addRenderableWidget(TacticalButton.compact(x + (full - 180) / 2, layout.backY(), 180,
                Component.translatable("screen.tacticaltablet.common.back"), ignored -> onClose()).withAccentBar(true));
    }

    @Override
    public void onClose() {
        TacticalTabletClientConfig.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try (TacticalUi.FrameScope ignored = TacticalUi.openFrame(frameClock.nextFrame(Util.getMillis(), false),
                com.makar.tacticaltablet.client.ExternalUiTheme.PALETTE)) {
            TacticalScreenBackground.render(graphics, minecraft, width, height);
            TacticalUi.drawPanel(graphics, layout.panelX(), layout.panelY(), layout.panelWidth(), layout.panelHeight());
            graphics.drawCenteredString(font, title, width / 2, layout.panelY() + 10,
                    TacticalUi.currentPalette().textPrimary());
            ModernDamageIntegration.Status status = ModernDamageIntegration.status();
            graphics.drawCenteredString(font,
                    Component.translatable("screen.tacticaltablet.mdc.version", status.detectedVersion()),
                    width / 2, layout.panelY() + 25,
                    status.supported() ? TacticalUi.currentPalette().success() : TacticalUi.currentPalette().danger());
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public com.makar.tacticaltablet.tablet.client.ui.UiPalette uiPalette() {
        return com.makar.tacticaltablet.client.ExternalUiTheme.PALETTE;
    }

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight,
                          int contentX, int contentY, int contentWidth, int backY) {
        static Layout calculate(int width, int height) {
            int panelWidth = Math.min(PANEL_WIDTH, Math.max(1, width - 12));
            int panelHeight = Math.min(PANEL_HEIGHT, Math.max(1, height - 12));
            int panelX = (width - panelWidth) / 2;
            int panelY = (height - panelHeight) / 2;
            return new Layout(panelX, panelY, panelWidth, panelHeight,
                    panelX + 14, panelY + 43, Math.max(1, panelWidth - 28),
                    panelY + panelHeight - 29);
        }
    }
}
