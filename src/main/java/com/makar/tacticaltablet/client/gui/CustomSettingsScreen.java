package com.makar.tacticaltablet.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.ControlsScreen;
import net.minecraft.network.chat.Component;

public final class CustomSettingsScreen extends Screen {

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 12;

    private final Screen parent;

    public CustomSettingsScreen(Screen parent) {
        super(Component.translatable("screen.tacticaltablet.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = (width - BUTTON_WIDTH) / 2;
        int y = height / 2 - 34;

        addSettingsButton(x, y, "screen.tacticaltablet.settings.graphics", this::openGraphicsSettings);
        y += BUTTON_HEIGHT + BUTTON_SPACING;
        addSettingsButton(x, y, "screen.tacticaltablet.settings.controls", this::openControls);
        y += BUTTON_HEIGHT + BUTTON_SPACING;
        addSettingsButton(x, y, "screen.tacticaltablet.common.back", this::onClose);
    }

    private void addSettingsButton(int x, int y, String translationKey, Runnable action) {
        addRenderableWidget(Button.builder(Component.translatable(translationKey), button -> action.run())
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private void openGraphicsSettings() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(GraphicsSettingsScreenFactory.create(this, minecraft.options));
    }

    private void openControls() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ControlsScreen(this, minecraft.options));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 72, 0xFFFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
