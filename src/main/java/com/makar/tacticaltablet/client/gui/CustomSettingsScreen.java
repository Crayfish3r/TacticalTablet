package com.makar.tacticaltablet.client.gui;

import com.makar.tacticaltablet.client.gui.component.TacticalSlider;
import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameClock;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameContext;
import com.makar.tacticaltablet.tablet.client.ui.widget.TacticalButton;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.ModListScreen;

public final class CustomSettingsScreen extends Screen {

    private static final String VIEWMODEL_TUNER_MOD_ID = "viewmodel_tuner";
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 230;
    private static final int PANEL_MARGIN = 10;
    private static final int CONTENT_WIDTH = 430;
    private static final int COLUMN_GAP = 8;
    private static final int ROW_GAP = 7;

    private final Screen parent;
    private final UiFrameClock frameClock = new UiFrameClock();
    private Layout layout;

    public CustomSettingsScreen(Screen parent) {
        super(Component.translatable("screen.tacticaltablet.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout = Layout.calculate(width, height);
        Minecraft minecraft = Minecraft.getInstance();
        int y = layout.firstControlY();

        int fov = minecraft.options.fov().get();
        addRenderableWidget(new TacticalSlider(
                layout.contentX(),
                y,
                layout.contentWidth(),
                TacticalTheme.CONTROL_HEIGHT,
                (fov - 30.0D) / 80.0D,
                value -> Component.translatable(
                        "screen.tacticaltablet.settings.fov_value",
                        Math.round(30.0D + value * 80.0D)
                ),
                value -> minecraft.options.fov().set((int) Math.round(30.0D + value * 80.0D))
        ));

        y += TacticalTheme.CONTROL_HEIGHT + ROW_GAP;
        addButton(layout.contentX(), y, layout.columnWidth(),
                "screen.tacticaltablet.settings.graphics", this::openGraphicsSettings);
        addButton(layout.rightColumnX(), y, layout.columnWidth(),
                "screen.tacticaltablet.settings.sounds", this::openSoundSettings);

        y += TacticalTheme.CONTROL_HEIGHT + ROW_GAP;
        addButton(layout.contentX(), y, layout.columnWidth(),
                "screen.tacticaltablet.settings.controls", this::openControls);
        addButton(layout.rightColumnX(), y, layout.columnWidth(),
                "screen.tacticaltablet.settings.mods", this::openMods);

        y += TacticalTheme.CONTROL_HEIGHT + ROW_GAP;
        TacticalButton viewmodelButton = addButton(
                layout.contentX(), y, layout.contentWidth(),
                "screen.tacticaltablet.settings.weapon_display", this::openWeaponDisplaySettings
        );
        viewmodelButton.active = ForgeModConfigScreenFactory.isAvailable(VIEWMODEL_TUNER_MOD_ID);
        if (!viewmodelButton.active) {
            viewmodelButton.withTooltip(Component.translatable(
                    "screen.tacticaltablet.settings.weapon_display_unavailable"));
        }

        y += TacticalTheme.CONTROL_HEIGHT + ROW_GAP;
        addButton(layout.contentX(), y, layout.contentWidth(),
                "screen.tacticaltablet.common.back", this::onClose);
    }

    private TacticalButton addButton(int x, int y, int buttonWidth,
                                     String translationKey, Runnable action) {
        TacticalButton button = TacticalButton.standard(
                        x,
                        y,
                        buttonWidth,
                        Component.translatable(translationKey),
                        ignored -> action.run()
                )
                .withAccentBar(true);
        addRenderableWidget(button);
        return button;
    }

    private void openGraphicsSettings() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(GraphicsSettingsScreenFactory.create(this, minecraft.options));
    }

    private void openSoundSettings() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new CustomSoundSettingsScreen(this, minecraft.options));
    }

    private void openControls() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new CustomControlsScreen(this, minecraft.options));
    }

    private void openMods() {
        Minecraft.getInstance().setScreen(new ModListScreen(this));
    }

    private void openWeaponDisplaySettings() {
        Minecraft minecraft = Minecraft.getInstance();
        ForgeModConfigScreenFactory.create(VIEWMODEL_TUNER_MOD_ID, minecraft, this)
                .ifPresent(minecraft::setScreen);
    }

    @Override
    public void removed() {
        Minecraft.getInstance().options.save();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiFrameContext frame = frameClock.nextFrame(Util.getMillis(), reducedMotion());
        try (TacticalUi.FrameScope ignored = TacticalUi.openFrame(frame)) {
            TacticalScreenBackground.render(graphics, minecraft, width, height);
            TacticalUi.drawPanel(graphics, layout.panelX(), layout.panelY(),
                    layout.panelWidth(), layout.panelHeight());
            graphics.drawCenteredString(font, title, width / 2, layout.titleY(),
                    TacticalTheme.TEXT_PRIMARY);
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

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight,
                          int contentX, int contentWidth, int columnWidth, int rightColumnX,
                          int titleY, int dividerY, int firstControlY) {
        private static Layout calculate(int screenWidth, int screenHeight) {
            int panelWidth = Math.min(PANEL_WIDTH, Math.max(1, screenWidth - PANEL_MARGIN * 2));
            int panelHeight = Math.min(PANEL_HEIGHT, Math.max(1, screenHeight - PANEL_MARGIN * 2));
            int panelX = (screenWidth - panelWidth) / 2;
            int panelY = (screenHeight - panelHeight) / 2;
            int contentWidth = Math.min(CONTENT_WIDTH, Math.max(1, panelWidth - 24));
            int contentX = panelX + (panelWidth - contentWidth) / 2;
            int columnWidth = Math.max(1, (contentWidth - COLUMN_GAP) / 2);
            int rightColumnX = contentX + columnWidth + COLUMN_GAP;
            int titleY = panelY + 11;
            int dividerY = panelY + 29;
            int firstControlY = panelY + 39;
            return new Layout(panelX, panelY, panelWidth, panelHeight, contentX,
                    contentWidth, columnWidth, rightColumnX,
                    titleY, dividerY, firstControlY);
        }
    }
}