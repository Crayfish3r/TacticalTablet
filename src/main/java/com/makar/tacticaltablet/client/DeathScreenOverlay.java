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
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class DeathScreenOverlay extends Screen {

    private static final int TEXT_COLOR = ExternalUiTheme.DANGER;
    private static final long FADE_IN_MS = 350L;
    private static final ResourceLocation SAD_THROMBONE = new ResourceLocation(TacticalTabletMod.MODID, "sad_thrombone");

    private final String titleText;
    private final String subtitleText;
    private final long startedAtMs;
    private final long durationMs;
    private List<FormattedCharSequence> titleLines = List.of();
    private List<FormattedCharSequence> subtitleLines = List.of();

    private DeathScreenOverlay(String title, String subtitle, int durationTicks) {
        super(narrationTitle(title, subtitle));
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
    protected void init() {
        int titleWidth = Math.max(40, (width - 48) / 2);
        int subtitleWidth = Math.max(40, width - 48);
        titleLines = limitedLines(font.split(Component.literal(titleText), titleWidth), 2);
        subtitleLines = limitedLines(font.split(Component.literal(subtitleText), subtitleWidth), 3);
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
        float fade = reducedMotion() ? 1.0F : Math.min(1.0F, elapsed / (float) FADE_IN_MS);
        int alpha = Math.min(255, (int) (255.0F * fade));
        int backgroundAlpha = Math.max(220, alpha);
        int textColor = alphaColor(TEXT_COLOR, alpha);

        graphics.fill(0, 0, width, height, backgroundAlpha << 24);

        int titleBlockHeight = titleLines.size() * font.lineHeight * 2;
        int subtitleBlockHeight = subtitleLines.size() * font.lineHeight;
        int totalHeight = titleBlockHeight + (subtitleLines.isEmpty() ? 0 : 12 + subtitleBlockHeight);
        int top = Math.max(20, height / 2 - totalHeight / 2);

        graphics.pose().pushPose();
        try {
            graphics.pose().translate(width / 2.0F, top, 0.0F);
            graphics.pose().scale(2.0F, 2.0F, 1.0F);
            for (int i = 0; i < titleLines.size(); i++) {
                graphics.drawCenteredString(font, titleLines.get(i), 0, i * font.lineHeight, textColor);
            }
        } finally {
            graphics.pose().popPose();
        }

        int subtitleY = top + titleBlockHeight + 12;
        for (int i = 0; i < subtitleLines.size(); i++) {
            graphics.drawCenteredString(font, subtitleLines.get(i), width / 2,
                    subtitleY + i * font.lineHeight, textColor);
        }
    }

    private boolean reducedMotion() {
        return minecraft != null && minecraft.options.screenEffectScale().get() <= 0.0D;
    }

    private static List<FormattedCharSequence> limitedLines(List<FormattedCharSequence> lines, int maximum) {
        return List.copyOf(lines.subList(0, Math.min(maximum, lines.size())));
    }

    private static Component narrationTitle(String title, String subtitle) {
        String safeTitle = title == null ? "" : title;
        String safeSubtitle = subtitle == null ? "" : subtitle;
        return safeSubtitle.isEmpty()
                ? Component.literal(safeTitle)
                : Component.literal(safeTitle + ". " + safeSubtitle);
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
