package com.makar.tacticaltablet.client.gui;

import com.makar.tacticaltablet.client.gui.component.TextureMenuButton;
import com.makar.tacticaltablet.client.gui.render.TabletMenuRenderer;
import com.makar.tacticaltablet.client.ExternalUiTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameClock;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameContext;
import com.makar.tacticaltablet.tablet.client.ui.animation.AnimatedFloat;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public final class CustomPauseScreen extends Screen implements com.makar.tacticaltablet.tablet.client.ui.UiPaletteProvider {

    private static final float ENTRANCE_DURATION_SECONDS = 0.30F;
    private static final float BUTTON_STAGGER = 0.08F;

    private final UiFrameClock frameClock = new UiFrameClock();
    private final AnimatedFloat entrance = new AnimatedFloat(0.0F, ENTRANCE_DURATION_SECONDS);
    private final List<TextureMenuButton> menuButtons = new ArrayList<>();
    private TabletMenuLayout menuLayout;

    public CustomPauseScreen() {
        super(Component.translatable("screen.tacticaltablet.main_menu.title"));
    }

    @Override
    protected void init() {
        menuLayout = TabletMenuLayout.calculatePause(width, height);
        menuButtons.clear();
        entrance.snapTo(0.0F);
        entrance.setTarget(1.0F);

        addMenuButton(
                MenuTextureSet.CONTINUE,
                "screen.tacticaltablet.pause.resume",
                this::resumeGame
        );
        addMenuButton(
                MenuTextureSet.INFO,
                "screen.tacticaltablet.main_menu.guide",
                () -> minecraft.setScreen(new GuideScreen(this))
        );
        addMenuButton(
                MenuTextureSet.SETTINGS,
                "screen.tacticaltablet.main_menu.settings",
                () -> minecraft.setScreen(new CustomSettingsScreen(this))
        );
        addMenuButton(
                MenuTextureSet.EXIT,
                "screen.tacticaltablet.pause.quit",
                this::disconnectToMenu
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

    private void resumeGame() {
        minecraft.setScreen(null);
        minecraft.mouseHandler.grabMouse();
    }

    private void disconnectToMenu() {
        if (minecraft.level != null) {
            minecraft.level.disconnect();
            minecraft.clearLevel();
        }
        minecraft.setScreen(new CustomMainMenu());
    }

    @Override
    public void onClose() {
        resumeGame();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiFrameContext frame = frameClock.nextFrame(Util.getMillis(), reducedMotion());
        try (TacticalUi.FrameScope ignored = TacticalUi.openFrame(frame, ExternalUiTheme.PALETTE)) {
            updateEntrance(frame);
            TabletMenuRenderer.render(
                    graphics,
                    menuLayout,
                    MenuTextureSet.PAUSE_TABLET,
                    entrance.value()
            );
            super.render(graphics, mouseX, mouseY, partialTick);
        }
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
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public com.makar.tacticaltablet.tablet.client.ui.UiPalette uiPalette() {
        return ExternalUiTheme.PALETTE;
    }
}
