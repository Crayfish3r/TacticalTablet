package com.makar.tacticaltablet.client;

import com.makar.tacticaltablet.airdrop.client.AirdropNoticeClientState;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, value = Dist.CLIENT)
public final class AirdropNoticeOverlay {
    private static final int MAX_BANNER_WIDTH = 280;
    private static final int PAD_X = 8;
    private static final int PAD_Y = 5;

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
        int screenHeight = event.getWindow().getGuiScaledHeight();
        String message = AirdropNoticeClientState.message();
        int contentWidth = Math.max(80, Math.min(MAX_BANNER_WIDTH - PAD_X * 2, screenWidth - 36));
        List<FormattedCharSequence> wrapped = minecraft.font.split(Component.literal(message), contentWidth);
        List<FormattedCharSequence> lines = wrapped.subList(0, Math.min(2, wrapped.size()));
        int lineCount = Math.max(1, lines.size());
        int bannerWidth = Math.min(MAX_BANNER_WIDTH, contentWidth + PAD_X * 2);
        int bannerHeight = PAD_Y * 2 + minecraft.font.lineHeight * (lineCount + 1);
        HudAnchorManager.Rect anchor = HudAnchorManager.topCenter(
                screenWidth, screenHeight, bannerWidth, bannerHeight);
        float alpha = AirdropNoticeClientState.alpha();
        int accent = HudColorPolicy.readableAccent(AirdropNoticeClientState.color());
        int borderColor = withAlpha(accent, alpha);
        int backgroundColor = withAlpha(TacticalTheme.SURFACE, alpha * 0.94F);
        int textColor = withAlpha(TacticalTheme.TEXT_PRIMARY, alpha);

        TacticalUi.drawCutCornerBorder(graphics, anchor.x(), anchor.y(), anchor.width(), anchor.height(),
                TacticalTheme.CORNER_CUT, 1, borderColor, backgroundColor);
        graphics.drawCenteredString(minecraft.font,
                Component.translatable("hud.tacticaltablet.airdrop"),
                anchor.x() + anchor.width() / 2, anchor.y() + PAD_Y, borderColor);
        int textY = anchor.y() + PAD_Y + minecraft.font.lineHeight;
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawCenteredString(minecraft.font, lines.get(i),
                    anchor.x() + anchor.width() / 2, textY + i * minecraft.font.lineHeight, textColor);
        }
    }

    private static int withAlpha(int color, float alpha) {
        int baseAlpha = color >>> 24;
        int adjustedAlpha = Math.max(0, Math.min(255, Math.round(baseAlpha * alpha)));
        return (color & 0x00FFFFFF) | (adjustedAlpha << 24);
    }
}
