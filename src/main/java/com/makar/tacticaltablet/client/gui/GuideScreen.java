package com.makar.tacticaltablet.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class GuideScreen extends Screen {

    private final Screen parent;

    public GuideScreen(Screen parent) {
        super(Component.translatable("screen.tacticaltablet.guide.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.tacticaltablet.common.back"),
                        button -> onClose()
                )
                .bounds(width / 2 - 100, height / 2 + 34, 200, 20)
                .build());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 46, 0xFFFFFFFF);
        graphics.drawCenteredString(
                font,
                Component.translatable("screen.tacticaltablet.common.coming_soon"),
                width / 2,
                height / 2 - 10,
                0xFFAAAAAA
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
