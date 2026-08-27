package com.makar.tacticaltablet.client.gui;

import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameClock;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameContext;
import com.makar.tacticaltablet.tablet.client.ui.widget.TacticalButton;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MouseSettingsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CustomControlsScreen extends Screen implements com.makar.tacticaltablet.tablet.client.ui.UiPaletteProvider {

    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 252;
    private static final int PANEL_MARGIN = 10;
    private static final int CONTENT_WIDTH = 430;
    private static final int COLUMN_GAP = 8;
    private static final int ROW_GAP = 7;

    private final Screen parent;
    private final Options options;
    private final UiFrameClock frameClock = new UiFrameClock();
    private Layout layout;

    public CustomControlsScreen(Screen parent, Options options) {
        super(Component.translatable("screen.tacticaltablet.controls.title"));
        this.parent = parent;
        this.options = options;
    }

    @Override
    protected void init() {
        layout = Layout.calculate(width, height);
        int y = layout.firstControlY();

        addRenderableWidget(TacticalButton.standard(
                        layout.contentX(),
                        y,
                        layout.columnWidth(),
                        Component.translatable("options.mouse_settings"),
                        ignored -> minecraft.setScreen(new MouseSettingsScreen(this, options))
                )
                .withAccentBar(true));
        addRenderableWidget(TacticalButton.standard(
                        layout.rightColumnX(),
                        y,
                        layout.columnWidth(),
                        Component.translatable("screen.tacticaltablet.controls.keybinds"),
                        ignored -> minecraft.setScreen(new FilteredKeyBindsScreen(this, options))
                )
                .withAccentBar(true));

        y += TacticalTheme.CONTROL_HEIGHT + ROW_GAP;
        addBooleanButton(options.toggleCrouch(), "key.sneak", layout.contentX(), y,
                layout.columnWidth());
        addBooleanButton(options.toggleSprint(), "key.sprint", layout.rightColumnX(), y,
                layout.columnWidth());

        y += TacticalTheme.CONTROL_HEIGHT + ROW_GAP;
        addBooleanButton(options.autoJump(), "options.autoJump", layout.contentX(), y,
                layout.contentWidth(), true);

        int backWidth = Math.min(200, layout.contentWidth());
        addRenderableWidget(TacticalButton.compact(
                        layout.contentX() + (layout.contentWidth() - backWidth) / 2,
                        layout.backY(),
                        backWidth,
                        Component.translatable("screen.tacticaltablet.common.back"),
                        ignored -> onClose()
                )
                .withAccentBar(true));
    }

    private void addBooleanButton(OptionInstance<Boolean> option, String labelKey,
                                  int x, int y, int width) {
        addBooleanButton(option, labelKey, x, y, width, false);
    }

    private void addBooleanButton(OptionInstance<Boolean> option, String labelKey,
                                  int x, int y, int width, boolean onOff) {
        TacticalButton[] holder = new TacticalButton[1];
        holder[0] = TacticalButton.standard(
                        x,
                        y,
                        width,
                        booleanLabel(labelKey, option.get(), onOff),
                        ignored -> {
                            boolean next = !option.get();
                            option.set(next);
                            holder[0].setMessage(booleanLabel(labelKey, next, onOff));
                        }
                )
                .withAccentBar(true);
        addRenderableWidget(holder[0]);
    }

    private static Component booleanLabel(String labelKey, boolean toggle, boolean onOff) {
        return Component.translatable(labelKey)
                .append(": ")
                .append(Component.translatable(onOff
                        ? (toggle ? "options.on" : "options.off")
                        : (toggle ? "options.key.toggle" : "options.key.hold")));
    }

    @Override
    public void onClose() {
        options.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiFrameContext frame = frameClock.nextFrame(Util.getMillis(), reducedMotion());
        try (TacticalUi.FrameScope ignored = TacticalUi.openFrame(frame,
                com.makar.tacticaltablet.client.ExternalUiTheme.PALETTE)) {
            TacticalScreenBackground.render(graphics, minecraft, width, height);
            TacticalUi.drawPanel(graphics, layout.panelX(), layout.panelY(),
                    layout.panelWidth(), layout.panelHeight());
            graphics.drawCenteredString(font, title, width / 2, layout.titleY(),
                    TacticalUi.currentPalette().textPrimary());
            TacticalUi.drawDivider(graphics, layout.contentX(), layout.dividerY(),
                    layout.contentWidth(), false);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private boolean reducedMotion() {
        return minecraft != null && minecraft.options.screenEffectScale().get() <= 0.0D;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public com.makar.tacticaltablet.tablet.client.ui.UiPalette uiPalette() {
        return com.makar.tacticaltablet.client.ExternalUiTheme.PALETTE;
    }

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight,
                          int contentX, int contentWidth, int columnWidth, int rightColumnX,
                          int titleY, int dividerY, int firstControlY, int backY) {
        private static Layout calculate(int screenWidth, int screenHeight) {
            int panelWidth = Math.min(PANEL_WIDTH, Math.max(1, screenWidth - PANEL_MARGIN * 2));
            int panelHeight = Math.min(PANEL_HEIGHT, Math.max(1, screenHeight - PANEL_MARGIN * 2));
            int panelX = (screenWidth - panelWidth) / 2;
            int panelY = (screenHeight - panelHeight) / 2;
            int contentWidth = Math.min(CONTENT_WIDTH, Math.max(1, panelWidth - 28));
            int contentX = panelX + (panelWidth - contentWidth) / 2;
            int columnWidth = Math.max(1, (contentWidth - COLUMN_GAP) / 2);
            int rightColumnX = contentX + columnWidth + COLUMN_GAP;
            return new Layout(panelX, panelY, panelWidth, panelHeight, contentX, contentWidth,
                    columnWidth, rightColumnX, panelY + 11, panelY + 31, panelY + 43,
                    panelY + panelHeight - 16 - TacticalTheme.CONTROL_HEIGHT_COMPACT);
        }
    }
}
