package com.makar.tacticaltablet.integration.moderndamage.client;

import com.makar.tacticaltablet.client.ExternalUiTheme;
import com.makar.tacticaltablet.client.HudAnchorManager;
import com.makar.tacticaltablet.client.gui.TacticalScreenBackground;
import com.makar.tacticaltablet.client.gui.component.TacticalSlider;
import com.makar.tacticaltablet.core.TacticalTabletClientConfig;
import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameClock;
import com.makar.tacticaltablet.tablet.client.ui.UiPalette;
import com.makar.tacticaltablet.tablet.client.ui.UiPaletteProvider;
import com.makar.tacticaltablet.tablet.client.ui.widget.TacticalButton;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Minimal transparent editor that previews every HUD change in its final screen position. */
public final class ModernDamageHudEditorScreen extends Screen implements UiPaletteProvider {
    private static final int PANEL_WIDTH = 600;
    private static final int PANEL_HEIGHT = 86;
    private static final int GAP = 6;

    private final Screen parent;
    private final UiFrameClock frameClock = new UiFrameClock();
    private Layout layout;
    private TacticalButton enabledButton;
    private TacticalButton sideButton;

    public ModernDamageHudEditorScreen(Screen parent) {
        super(Component.translatable("screen.tacticaltablet.mdc.hud_editor.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout = Layout.calculate(width, height);
        int quarter = Math.max(1, (layout.contentWidth() - GAP * 3) / 4);
        int half = Math.max(1, (layout.contentWidth() - GAP) / 2);
        int x = layout.contentX();
        int y = layout.panelY() + 6;

        enabledButton = TacticalButton.compact(x, y, quarter, enabledLabel(), ignored -> toggleEnabled())
                .withAccentBar(true);
        sideButton = TacticalButton.compact(x + quarter + GAP, y, quarter, sideLabel(), ignored -> cycleSide())
                .withAccentBar(true);
        addRenderableWidget(enabledButton);
        addRenderableWidget(sideButton);
        addRenderableWidget(TacticalButton.compact(x + (quarter + GAP) * 2, y, quarter,
                Component.translatable("screen.tacticaltablet.mdc.hud_editor.reset"), ignored -> reset())
                .withAccentColor(ExternalUiTheme.SECONDARY).withAccentBar(true));
        addRenderableWidget(TacticalButton.compact(x + (quarter + GAP) * 3, y, quarter,
                Component.translatable("screen.tacticaltablet.mdc.hud_editor.done"), ignored -> onClose())
                .withAccentBar(true));

        y += 25;
        addRenderableWidget(new TacticalSlider(x, y, half, TacticalTheme.CONTROL_HEIGHT_COMPACT,
                (TacticalTabletClientConfig.MDC_STAMINA_HUD_SCALE.get() - 0.5D) / 1.5D,
                value -> Component.translatable("screen.tacticaltablet.mdc.scale",
                        Math.round((0.5D + value * 1.5D) * 100.0D)),
                value -> TacticalTabletClientConfig.MDC_STAMINA_HUD_SCALE.set(0.5D + value * 1.5D)));
        addRenderableWidget(new TacticalSlider(x + half + GAP, y, half, TacticalTheme.CONTROL_HEIGHT_COMPACT,
                (TacticalTabletClientConfig.MDC_STAMINA_HUD_OPACITY.get() - 0.25D) / 0.75D,
                value -> Component.translatable("screen.tacticaltablet.mdc.opacity",
                        Math.round((0.25D + value * 0.75D) * 100.0D)),
                value -> TacticalTabletClientConfig.MDC_STAMINA_HUD_OPACITY.set(0.25D + value * 0.75D)));

        y += 23;
        addRenderableWidget(new TacticalSlider(x, y, half, TacticalTheme.CONTROL_HEIGHT_COMPACT,
                (TacticalTabletClientConfig.MDC_STAMINA_HUD_X_OFFSET.get() + 200.0D) / 400.0D,
                value -> Component.translatable("screen.tacticaltablet.mdc.x_offset",
                        Math.round(-200.0D + value * 400.0D)),
                value -> TacticalTabletClientConfig.MDC_STAMINA_HUD_X_OFFSET.set(
                        (int) Math.round(-200.0D + value * 400.0D))));
        addRenderableWidget(new TacticalSlider(x + half + GAP, y, half, TacticalTheme.CONTROL_HEIGHT_COMPACT,
                (TacticalTabletClientConfig.MDC_STAMINA_HUD_Y_OFFSET.get() + 120.0D) / 240.0D,
                value -> Component.translatable("screen.tacticaltablet.mdc.y_offset",
                        Math.round(-120.0D + value * 240.0D)),
                value -> TacticalTabletClientConfig.MDC_STAMINA_HUD_Y_OFFSET.set(
                        (int) Math.round(-120.0D + value * 240.0D))));
    }

    private void toggleEnabled() {
        TacticalTabletClientConfig.MDC_STAMINA_HUD_ENABLED.set(
                !TacticalTabletClientConfig.MDC_STAMINA_HUD_ENABLED.get());
        enabledButton.setMessage(enabledLabel());
    }

    private void cycleSide() {
        TacticalTabletClientConfig.StaminaHudSide current = TacticalTabletClientConfig.MDC_STAMINA_HUD_SIDE.get();
        TacticalTabletClientConfig.StaminaHudSide[] values = TacticalTabletClientConfig.StaminaHudSide.values();
        TacticalTabletClientConfig.MDC_STAMINA_HUD_SIDE.set(values[(current.ordinal() + 1) % values.length]);
        sideButton.setMessage(sideLabel());
    }

    private void reset() {
        TacticalTabletClientConfig.MDC_STAMINA_HUD_ENABLED.set(true);
        TacticalTabletClientConfig.MDC_STAMINA_HUD_SIDE.set(TacticalTabletClientConfig.StaminaHudSide.AUTO);
        TacticalTabletClientConfig.MDC_STAMINA_HUD_X_OFFSET.set(0);
        TacticalTabletClientConfig.MDC_STAMINA_HUD_Y_OFFSET.set(0);
        TacticalTabletClientConfig.MDC_STAMINA_HUD_SCALE.set(1.0D);
        TacticalTabletClientConfig.MDC_STAMINA_HUD_OPACITY.set(0.9D);
        rebuildWidgets();
    }

    private Component enabledLabel() {
        return Component.translatable("screen.tacticaltablet.mdc.hud_enabled").append(": ")
                .append(Component.translatable(TacticalTabletClientConfig.MDC_STAMINA_HUD_ENABLED.get()
                        ? "options.on" : "options.off"));
    }

    private Component sideLabel() {
        String suffix = TacticalTabletClientConfig.MDC_STAMINA_HUD_SIDE.get().name()
                .toLowerCase(java.util.Locale.ROOT);
        return Component.translatable("screen.tacticaltablet.mdc.position").append(": ")
                .append(Component.translatable("screen.tacticaltablet.mdc.position." + suffix));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try (TacticalUi.FrameScope ignored = TacticalUi.openFrame(
                frameClock.nextFrame(Util.getMillis(), false), ExternalUiTheme.PALETTE)) {
            TacticalScreenBackground.renderBase(graphics, minecraft, width, height);
            ModernDamageStaminaOverlay.renderPreview(graphics, width, height, List.of(layout.panelRect()));
            TacticalUi.drawPanel(graphics, layout.panelX(), layout.panelY(), layout.panelWidth(), PANEL_HEIGHT);
            graphics.drawCenteredString(font,
                    Component.translatable("screen.tacticaltablet.mdc.hud_editor.hint"),
                    width / 2, Math.max(4, layout.panelY() - 13), ExternalUiTheme.TEXT_PRIMARY);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void onClose() {
        TacticalTabletClientConfig.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public UiPalette uiPalette() {
        return ExternalUiTheme.PALETTE;
    }

    private record Layout(int panelX, int panelY, int panelWidth, int contentX, int contentWidth) {
        static Layout calculate(int width, int height) {
            int panelWidth = Math.min(PANEL_WIDTH, Math.max(1, width - 8));
            int panelX = (width - panelWidth) / 2;
            int panelY = Math.max(4, height - PANEL_HEIGHT - 5);
            return new Layout(panelX, panelY, panelWidth, panelX + 7, Math.max(1, panelWidth - 14));
        }

        HudAnchorManager.Rect panelRect() {
            return new HudAnchorManager.Rect(panelX, panelY, panelWidth, PANEL_HEIGHT);
        }
    }
}
