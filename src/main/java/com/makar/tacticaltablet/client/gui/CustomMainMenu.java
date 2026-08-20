package com.makar.tacticaltablet.client.gui;

import com.makar.tacticaltablet.client.gui.component.GlassButton;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameClock;
import com.makar.tacticaltablet.tablet.client.ui.UiFrameContext;
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

public final class CustomMainMenu extends Screen {

    static final String SERVER_ADDRESS = "deluxewarfare.sosal.today";

    private static final String SERVER_NAME = "DeluxeWarfare";
    private static final ResourceLocation BACKGROUND = new ResourceLocation(
            TacticalTabletMod.MODID,
            "textures/gui/main_menu/background_blurred.png"
    );

    private static final int BACKGROUND_TEXTURE_WIDTH = 1672;
    private static final int BACKGROUND_TEXTURE_HEIGHT = 941;
    private static final float BACKGROUND_ZOOM = 1.05F;
    private static final float PARALLAX_X = 6.0F;
    private static final float PARALLAX_Y = 4.0F;
    private static final float PARALLAX_RESPONSE = 4.5F;

    private static final int NORMAL_BUTTON_WIDTH = 180;
    private static final int NORMAL_BUTTON_HEIGHT = 34;
    private static final int PLAY_BUTTON_WIDTH = 220;
    private static final int PLAY_BUTTON_HEIGHT = 40;
    private static final int MIN_BUTTON_WIDTH = 80;
    private static final int SMALL_VIEW_MARGIN = 8;
    private static final int DEFAULT_VIEW_MARGIN = 16;
    private static final int DARK_OVERLAY = 0x2C000000;
    private static final int VIGNETTE_ALPHA = 38;
    private static final int VIGNETTE_STEPS = 14;
    private static final int TITLE_COLOR = 0xDDE8EDF2;

    private final UiFrameClock frameClock = new UiFrameClock();
    private float backgroundOffsetX;
    private float backgroundOffsetY;
    private MenuLayout menuLayout;

    public CustomMainMenu() {
        super(Component.translatable("screen.tacticaltablet.main_menu.title"));
    }

    @Override
    protected void init() {
        menuLayout = MenuLayout.calculate(width, height);

        addRenderableWidget(new GlassButton(
                menuLayout.playX,
                menuLayout.playY,
                menuLayout.playWidth,
                menuLayout.playHeight,
                Component.translatable("screen.tacticaltablet.main_menu.play"),
                GlassButton.ButtonStyle.PLAY,
                button -> connectToServer()
        ));
        addRenderableWidget(new GlassButton(
                menuLayout.leftX,
                menuLayout.topY,
                menuLayout.normalWidth,
                menuLayout.normalHeight,
                Component.translatable("screen.tacticaltablet.main_menu.rules"),
                GlassButton.ButtonStyle.NORMAL,
                button -> Minecraft.getInstance().setScreen(new RulesScreen(this))
        ));
        addRenderableWidget(new GlassButton(
                menuLayout.rightX,
                menuLayout.topY,
                menuLayout.normalWidth,
                menuLayout.normalHeight,
                Component.translatable("screen.tacticaltablet.main_menu.guide"),
                GlassButton.ButtonStyle.NORMAL,
                button -> Minecraft.getInstance().setScreen(new GuideScreen(this))
        ));
        addRenderableWidget(new GlassButton(
                menuLayout.leftX,
                menuLayout.bottomY,
                menuLayout.normalWidth,
                menuLayout.normalHeight,
                Component.translatable("screen.tacticaltablet.main_menu.settings"),
                GlassButton.ButtonStyle.NORMAL,
                button -> Minecraft.getInstance().setScreen(new CustomSettingsScreen(this))
        ));
        addRenderableWidget(new GlassButton(
                menuLayout.rightX,
                menuLayout.bottomY,
                menuLayout.normalWidth,
                menuLayout.normalHeight,
                Component.translatable("screen.tacticaltablet.main_menu.quit"),
                GlassButton.ButtonStyle.DANGER,
                button -> Minecraft.getInstance().stop()
        ));
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
        try (TacticalUi.FrameScope ignored = TacticalUi.openFrame(frame)) {
            updateParallax(mouseX, mouseY, frame);
            renderBackground(graphics);
            renderOverlay(graphics);
            renderMenu(graphics);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void updateParallax(int mouseX, int mouseY, UiFrameContext frame) {
        if (frame.reducedMotion() || width <= 0 || height <= 0) {
            backgroundOffsetX = 0.0F;
            backgroundOffsetY = 0.0F;
            return;
        }

        float normalizedX = Mth.clamp(mouseX / (float) width * 2.0F - 1.0F, -1.0F, 1.0F);
        float normalizedY = Mth.clamp(mouseY / (float) height * 2.0F - 1.0F, -1.0F, 1.0F);
        float targetX = -normalizedX * PARALLAX_X;
        float targetY = -normalizedY * PARALLAX_Y;
        float smoothing = 1.0F - (float) Math.exp(-PARALLAX_RESPONSE * frame.deltaSeconds());

        backgroundOffsetX += (targetX - backgroundOffsetX) * smoothing;
        backgroundOffsetY += (targetY - backgroundOffsetY) * smoothing;
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        float coverScale = Math.max(
                width / (float) BACKGROUND_TEXTURE_WIDTH,
                height / (float) BACKGROUND_TEXTURE_HEIGHT
        ) * BACKGROUND_ZOOM;
        int drawWidth = Math.max(1, (int) Math.ceil(BACKGROUND_TEXTURE_WIDTH * coverScale));
        int drawHeight = Math.max(1, (int) Math.ceil(BACKGROUND_TEXTURE_HEIGHT * coverScale));
        int drawX = (width - drawWidth) / 2 + Math.round(backgroundOffsetX);
        int drawY = (height - drawHeight) / 2 + Math.round(backgroundOffsetY);

        graphics.blit(
                BACKGROUND,
                drawX,
                drawY,
                drawWidth,
                drawHeight,
                0.0F,
                0.0F,
                BACKGROUND_TEXTURE_WIDTH,
                BACKGROUND_TEXTURE_HEIGHT,
                BACKGROUND_TEXTURE_WIDTH,
                BACKGROUND_TEXTURE_HEIGHT
        );
    }

    private void renderOverlay(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, DARK_OVERLAY);

        int verticalDepth = Math.max(24, height / 5);
        int edgeColor = VIGNETTE_ALPHA << 24;
        graphics.fillGradient(0, 0, width, verticalDepth, edgeColor, 0x00000000);
        graphics.fillGradient(0, height - verticalDepth, width, height, 0x00000000, edgeColor);

        int horizontalDepth = Math.max(32, width / 7);
        for (int step = 0; step < VIGNETTE_STEPS; step++) {
            float progress = step / (float) VIGNETTE_STEPS;
            int alpha = Math.round(VIGNETTE_ALPHA * (1.0F - progress) * (1.0F - progress));
            int color = alpha << 24;
            int leftStart = step * horizontalDepth / VIGNETTE_STEPS;
            int leftEnd = (step + 1) * horizontalDepth / VIGNETTE_STEPS;
            graphics.fill(leftStart, 0, leftEnd, height, color);
            graphics.fill(width - leftEnd, 0, width - leftStart, height, color);
        }
    }

    private void renderMenu(GuiGraphics graphics) {
        if (menuLayout == null) return;
        int titleX = (width - font.width(title)) / 2;
        graphics.drawString(font, title, titleX, Math.max(8, menuLayout.topY - 28), TITLE_COLOR, false);
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

    record MenuLayout(
            int leftX,
            int rightX,
            int topY,
            int bottomY,
            int playX,
            int playY,
            int normalWidth,
            int normalHeight,
            int playWidth,
            int playHeight
    ) {
        static MenuLayout calculate(int screenWidth, int screenHeight) {
            int centerX = screenWidth / 2;
            int centerY = screenHeight / 2;
            int margin = screenWidth < 360 ? SMALL_VIEW_MARGIN : DEFAULT_VIEW_MARGIN;
            int horizontalGap = Mth.clamp(screenWidth / 9, 16, 56);
            int availableButtonWidth = Math.max(
                    MIN_BUTTON_WIDTH,
                    (screenWidth - margin * 2 - horizontalGap) / 2
            );
            int normalWidth = Math.min(NORMAL_BUTTON_WIDTH, availableButtonWidth);
            int normalHeight = screenHeight < 220 ? 30 : NORMAL_BUTTON_HEIGHT;
            int playWidth = Math.min(PLAY_BUTTON_WIDTH, Math.max(MIN_BUTTON_WIDTH, screenWidth - margin * 2));
            int playHeight = screenHeight < 220 ? 36 : PLAY_BUTTON_HEIGHT;
            int verticalGap = Mth.clamp((screenHeight - 160) / 8, 10, 24);
            int clusterHeight = normalHeight * 2 + playHeight + verticalGap * 2;
            int topY = Math.max(12, centerY - clusterHeight / 2);
            int playY = topY + normalHeight + verticalGap;
            int bottomY = playY + playHeight + verticalGap;
            int leftX = centerX - horizontalGap / 2 - normalWidth;
            int rightX = centerX + (horizontalGap + 1) / 2;
            int playX = centerX - playWidth / 2;

            return new MenuLayout(
                    leftX,
                    rightX,
                    topY,
                    bottomY,
                    playX,
                    playY,
                    normalWidth,
                    normalHeight,
                    playWidth,
                    playHeight
            );
        }
    }
}
