package com.makar.tacticaltablet.airdrop.client;

import com.makar.tacticaltablet.airdrop.net.AirdropNoticePacket;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;

public final class AirdropNoticeClientState {
    private static final int DEFAULT_FADE_IN_TICKS = 8;
    private static final int DEFAULT_FADE_OUT_TICKS = 20;

    private static String message = "";
    private static int color = 0xFFFF3333;
    private static int totalTicks = 0;
    private static int ticksLeft = 0;
    private static int fadeInTicks = DEFAULT_FADE_IN_TICKS;
    private static int fadeOutTicks = DEFAULT_FADE_OUT_TICKS;

    private AirdropNoticeClientState() {
    }

    public static void handle(AirdropNoticePacket packet) {
        if (packet == null) return;
        show(packet.message(), packet.color(), packet.durationTicks(), packet.type(), true);
    }

    public static void show(
            String newMessage,
            int newColor,
            int durationTicks,
            AirdropNoticePacket.NoticeType type,
            boolean playSound
    ) {
        message = newMessage == null ? "" : newMessage;
        color = newColor;
        totalTicks = Math.max(0, durationTicks);
        ticksLeft = totalTicks;
        fadeInTicks = Math.min(DEFAULT_FADE_IN_TICKS, Math.max(1, totalTicks));
        fadeOutTicks = Math.min(DEFAULT_FADE_OUT_TICKS, Math.max(1, totalTicks));

        if (playSound) {
            playNoticeSound(type);
        }
    }

    public static void tick() {
        if (ticksLeft > 0) {
            ticksLeft--;
        }
    }

    public static boolean isVisible() {
        return ticksLeft > 0 && !message.isBlank();
    }

    public static String message() {
        return message;
    }

    public static int color() {
        return color;
    }

    public static float alpha() {
        return Math.max(0.0F, Math.min(1.0F, computeAlpha(ticksLeft, totalTicks, fadeInTicks, fadeOutTicks)));
    }

    public static int totalTicks() {
        return totalTicks;
    }

    public static int ticksLeft() {
        return ticksLeft;
    }

    public static int fadeInTicks() {
        return fadeInTicks;
    }

    public static int fadeOutTicks() {
        return fadeOutTicks;
    }

    private static float computeAlpha(int ticksLeft, int totalTicks, int fadeInTicks, int fadeOutTicks) {
        if (totalTicks <= 0) return 0.0F;

        int elapsed = totalTicks - ticksLeft;
        if (fadeInTicks > 0 && elapsed < fadeInTicks) {
            return elapsed / (float) fadeInTicks;
        }

        if (fadeOutTicks > 0 && ticksLeft < fadeOutTicks) {
            return ticksLeft / (float) fadeOutTicks;
        }

        return 1.0F;
    }

    private static void playNoticeSound(AirdropNoticePacket.NoticeType type) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        AirdropNoticePacket.NoticeType safeType = type == null
                ? AirdropNoticePacket.NoticeType.COUNTDOWN_60
                : type;
        float volume = safeType == AirdropNoticePacket.NoticeType.DROPPING ? 0.40F : 0.35F;
        float pitch = safeType == AirdropNoticePacket.NoticeType.DROPPING ? 0.78F : 0.90F;
        minecraft.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), volume, pitch);
    }
}
