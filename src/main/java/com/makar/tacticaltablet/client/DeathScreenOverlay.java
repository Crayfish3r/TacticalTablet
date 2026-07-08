package com.makar.tacticaltablet.client;

import com.makar.tacticaltablet.core.TacticalTabletMod;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public final class DeathScreenOverlay extends Screen {

    private static final int TEXT_COLOR = 0xFFFF3333;
    private static final long FADE_IN_MS = 350L;
    private static final ResourceLocation SAD_THROMBONE = new ResourceLocation(TacticalTabletMod.MODID, "sad_thrombone");

    private final String titleText;
    private final String subtitleText;
    private final long startedAtMs;
    private final long durationMs;

    private DeathScreenOverlay(String title, String subtitle, int durationTicks) {
        super(Component.empty());
        this.titleText = title == null ? "" : title;
        this.subtitleText = subtitle == null ? "" : subtitle;
        this.durationMs = Math.max(1, durationTicks) * 50L;
        this.startedAtMs = Util.getMillis();
    }

    public static void show(String newTitle, String newSubtitle, int durationTicks) {
        show(newTitle, newSubtitle, durationTicks, false);
    }

    public static void show(String newTitle, String newSubtitle, int durationTicks, boolean playSadTrombone) {
        Minecraft minecraft = Minecraft.getInstance();
        if (playSadTrombone) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvent.createVariableRangeEvent(SAD_THROMBONE),
                    1.0F,
                    1.0F
            ));
        }

        minecraft.setScreen(new DeathScreenOverlay(newTitle, newSubtitle, durationTicks));
    }

    public static boolean isActive() {
        return Minecraft.getInstance().screen instanceof DeathScreenOverlay;
    }

    @Override
    public void tick() {
        long elapsed = Util.getMillis() - startedAtMs;
        if (elapsed >= durationMs && minecraft != null && minecraft.screen == this) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long elapsed = Math.max(0L, Util.getMillis() - startedAtMs);
        float fade = Math.min(1.0F, elapsed / (float) FADE_IN_MS);
        int alpha = Math.min(255, (int) (255.0F * fade));
        int backgroundAlpha = Math.max(220, alpha);
        int textColor = alphaColor(TEXT_COLOR, alpha);

        graphics.fill(0, 0, width, height, backgroundAlpha << 24);

        graphics.pose().pushPose();
        graphics.pose().translate(width / 2.0F, height / 2.0F - 18.0F, 0.0F);
        graphics.pose().scale(2.0F, 2.0F, 1.0F);
        graphics.drawCenteredString(font, titleText, 0, 0, textColor);
        graphics.pose().popPose();

        graphics.drawCenteredString(
                font,
                subtitleText,
                width / 2,
                height / 2 + 12,
                textColor
        );
    }

    private static int alphaColor(int rgbColor, int alpha) {
        return ((Math.max(0, Math.min(255, alpha)) & 0xFF) << 24) | (rgbColor & 0x00FFFFFF);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return true;
    }
}
