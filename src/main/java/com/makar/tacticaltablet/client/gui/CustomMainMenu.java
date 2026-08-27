package com.makar.tacticaltablet.client.gui;

import com.makar.tacticaltablet.client.gui.component.TextureMenuButton;
import com.makar.tacticaltablet.client.gui.render.TabletMenuRenderer;
import com.makar.tacticaltablet.client.ExternalUiTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameClock;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameContext;
import com.makar.tacticaltablet.tablet.client.ui.animation.AnimatedFloat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public final class CustomMainMenu extends Screen implements com.makar.tacticaltablet.tablet.client.ui.UiPaletteProvider {

    static final String SERVER_ADDRESS = "deluxewarfare.sosal.today";

    private static final String SERVER_NAME = "DeluxeWarfare";
    private static final float ENTRANCE_DURATION_SECONDS = 0.34F;
    private static final float BUTTON_STAGGER = 0.08F;

    private final UiFrameClock frameClock = new UiFrameClock();
    private final AnimatedFloat entrance = new AnimatedFloat(0.0F, ENTRANCE_DURATION_SECONDS);
    private final List<TextureMenuButton> menuButtons = new ArrayList<>();
    private TabletMenuLayout menuLayout;

    public CustomMainMenu() {
        super(Component.translatable("screen.tacticaltablet.main_menu.title"));
    }

    @Override
    protected void init() {
        menuLayout = TabletMenuLayout.calculateMain(width, height);
        menuButtons.clear();
        entrance.snapTo(0.0F);
        entrance.setTarget(1.0F);

        addMenuButton(
                MenuTextureSet.JOIN,
                "screen.tacticaltablet.main_menu.play",
                this::connectToServer
        );
        addMenuButton(
                MenuTextureSet.INFO,
                "screen.tacticaltablet.main_menu.guide",
                () -> Minecraft.getInstance().setScreen(new GuideScreen(this))
        );
        addMenuButton(
                MenuTextureSet.SETTINGS,
                "screen.tacticaltablet.main_menu.settings",
                () -> Minecraft.getInstance().setScreen(new CustomSettingsScreen(this))
        );
        addMenuButton(
                MenuTextureSet.EXIT,
                "screen.tacticaltablet.main_menu.quit",
                () -> Minecraft.getInstance().stop()
        );

        updateButtonAnimation(0.0F);
    }

    private void addMenuButton(
            ResourceLocation texture,
            String narrationKey,
            Runnable action
    ) {
        int index = menuButtons.size();
        TextureMenuButton button = addRenderableWidget(new TextureMenuButton(
                menuLayout.buttonX(),
                menuLayout.buttonY(index),
                menuLayout.buttonWidth(),
                menuLayout.buttonHeight(),
                texture,
                Component.translatable(narrationKey),
                ignored -> action.run()
        ));
        menuButtons.add(button);
    }

    private void connectToServer() {
        Minecraft minecraft = Minecraft.getInstance();
        ServerData serverData = new ServerData(SERVER_NAME, SERVER_ADDRESS, false);
        ConnectScreen.startConnecting(
                this,
                minecraft,
                ServerAddress.parseString(SERVER_ADDRESS),
                serverData,
                false
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiFrameContext frame = frameClock.nextFrame(Util.getMillis(), reducedMotion());
        try (TacticalUi.FrameScope ignored = TacticalUi.openFrame(frame, ExternalUiTheme.PALETTE)) {
            updateEntrance(frame);
            renderBackground(graphics);
            TabletMenuRenderer.render(graphics, menuLayout, MenuTextureSet.TABLET, entrance.value());
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        float coverScale = Math.max(
                width / (float) MenuTextureSet.BACKGROUND_WIDTH,
                height / (float) MenuTextureSet.BACKGROUND_HEIGHT
        );
        int drawWidth = Math.max(1, (int) Math.ceil(MenuTextureSet.BACKGROUND_WIDTH * coverScale));
        int drawHeight = Math.max(1, (int) Math.ceil(MenuTextureSet.BACKGROUND_HEIGHT * coverScale));
        int drawX = (width - drawWidth) / 2;
        int drawY = (height - drawHeight) / 2;

        graphics.blit(
                MenuTextureSet.BACKGROUND,
                drawX,
                drawY,
                drawWidth,
                drawHeight,
                0.0F,
                0.0F,
                MenuTextureSet.BACKGROUND_WIDTH,
                MenuTextureSet.BACKGROUND_HEIGHT,
                MenuTextureSet.BACKGROUND_WIDTH,
                MenuTextureSet.BACKGROUND_HEIGHT
        );
    }

    private void updateEntrance(UiFrameContext frame) {
        if (frame.reducedMotion()) {
            entrance.snapTo(1.0F);
        } else {
            entrance.update(frame.deltaSeconds());
        }
        updateButtonAnimation(entrance.value());
    }

    private void updateButtonAnimation(float progress) {
        for (int index = 0; index < menuButtons.size(); index++) {
            float delay = index * BUTTON_STAGGER;
            float buttonProgress = Mth.clamp((progress - delay) / (1.0F - delay), 0.0F, 1.0F);
            menuButtons.get(index).setRevealProgress(buttonProgress, 10 + index * 2);
        }
    }

    private boolean reducedMotion() {
        return minecraft != null && minecraft.options.screenEffectScale().get() <= 0.0D;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public com.makar.tacticaltablet.tablet.client.ui.UiPalette uiPalette() {
        return ExternalUiTheme.PALETTE;
    }
}
