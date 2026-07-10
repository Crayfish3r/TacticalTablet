package com.makar.tacticaltablet.client;

import com.makar.tacticaltablet.airdrop.client.AirdropNoticeClientState;
import com.makar.tacticaltablet.core.TacticalTabletMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, value = Dist.CLIENT)
public final class AirdropNoticeOverlay {
    private static final int NOTICE_Y = 36;
    private static final int BACKGROUND_COLOR = 0x66000000;
    private static final int BORDER_COLOR = 0x99AA0000;
    private static final int PAD_X = 5;
    private static final int PAD_Y = 3;

    private AirdropNoticeOverlay() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        AirdropNoticeClientState.tick();
    }

    @SubscribeEvent
    public static void onRenderHotbar(RenderGuiOverlayEvent.Post event) {
        if (!VanillaGuiOverlay.HOTBAR.id().equals(event.getOverlay().id())) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;
        if (minecraft.screen != null) return;
        if (!AirdropNoticeClientState.isVisible()) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = event.getWindow().getGuiScaledWidth();
        String message = AirdropNoticeClientState.message();
        int x = screenWidth / 2;
        int y = NOTICE_Y;
        int textWidth = minecraft.font.width(message);
        int left = x - textWidth / 2 - PAD_X;
        int top = y - PAD_Y;
        int right = x + textWidth / 2 + PAD_X;
        int bottom = y + minecraft.font.lineHeight + PAD_Y;
        float alpha = AirdropNoticeClientState.alpha();
        int backgroundColor = withAlpha(BACKGROUND_COLOR, alpha);
        int borderColor = withAlpha(BORDER_COLOR, alpha);
        int textColor = withAlpha(AirdropNoticeClientState.color(), alpha);

        graphics.fill(left, top, right, bottom, backgroundColor);
        graphics.fill(left, top, right, top + 1, borderColor);
        graphics.fill(left, bottom - 1, right, bottom, borderColor);
        graphics.fill(left, top, left + 1, bottom, borderColor);
        graphics.fill(right - 1, top, right, bottom, borderColor);
        graphics.drawCenteredString(minecraft.font, message, x, y, textColor);
    }

    private static int withAlpha(int color, float alpha) {
        int baseAlpha = color >>> 24;
        int adjustedAlpha = Math.max(0, Math.min(255, Math.round(baseAlpha * alpha)));
        return (color & 0x00FFFFFF) | (adjustedAlpha << 24);
    }
}
