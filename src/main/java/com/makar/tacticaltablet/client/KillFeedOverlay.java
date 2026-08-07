package com.makar.tacticaltablet.client;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.net.KillFeedPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, value = Dist.CLIENT)
public final class KillFeedOverlay {
    private static final int CARD_WIDTH = 220;
    private static final int CARD_HEIGHT = 20;
    private static final int REWARD_CARD_HEIGHT = 31;
    private static final int PAD_X = 6;

    private KillFeedOverlay() { }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) KillFeedClientState.tick();
    }

    @SubscribeEvent
    public static void onRenderHotbar(RenderGuiOverlayEvent.Post event) {
        if (!VanillaGuiOverlay.HOTBAR.id().equals(event.getOverlay().id())) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null) return;

        renderEntries(event.getGuiGraphics(), event.getWindow().getGuiScaledWidth());
    }

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;
        renderEntries(event.getGuiGraphics(), minecraft.getWindow().getGuiScaledWidth());
    }

    private static void renderEntries(GuiGraphics graphics, int screenWidth) {
        Minecraft minecraft = Minecraft.getInstance();
        int width = Math.min(CARD_WIDTH, Math.max(80, screenWidth - 8));
        int x = Math.max(4, screenWidth - width - 6);
        for (KillFeedClientState.Entry entry : KillFeedClientState.entries()) {
            KillFeedPacket packet = entry.packet();
            int height = hasReward(packet) ? REWARD_CARD_HEIGHT : CARD_HEIGHT;
            int y = 8 + Math.round(entry.visualOffset());
            float alpha = entry.alpha();
            TacticalUi.drawCutCornerBorder(graphics, x, y, width, height,
                    TacticalTheme.CORNER_CUT, 1, withAlpha(TacticalTheme.ACCENT, alpha),
                    withAlpha(TacticalTheme.SURFACE, alpha * 0.90F));
            graphics.drawString(minecraft.font, format(packet, minecraft.font, width - PAD_X * 2),
                    x + PAD_X, y + 5, withAlpha(TacticalTheme.TEXT_PRIMARY, alpha), false);
            if (hasReward(packet)) {
                graphics.drawString(minecraft.font, ellipsize(minecraft.font, rewardText(packet), width - PAD_X * 2), x + PAD_X,
                        y + 5 + minecraft.font.lineHeight, withAlpha(TacticalTheme.WARNING, alpha), false);
            }
        }
    }

    static Component format(KillFeedPacket packet, Font font, int maxWidth) {
        String suffix = ellipsize(font, suffix(packet), Math.max(20, maxWidth / 2));
        int suffixWidth = suffix.isBlank() ? 0 : font.width("  " + suffix);
        int arrowWidth = packet.killerUuid() == null ? 0 : font.width(" → ");
        int namesWidth = Math.max(24, maxWidth - suffixWidth - arrowWidth);
        int killerWidth = packet.killerUuid() == null ? 0 : namesWidth / 2;
        int victimWidth = packet.killerUuid() == null ? namesWidth : namesWidth - killerWidth;

        MutableComponent result = Component.empty();
        if (packet.killerUuid() != null) {
            result.append(colored(ellipsize(font, packet.killerName(), killerWidth), packet.killerColor(), TacticalTheme.ACCENT));
            result.append(Component.literal(" → ").withStyle(Style.EMPTY.withColor(TacticalTheme.TEXT_SECONDARY & 0x00FFFFFF)));
        }
        result.append(colored(ellipsize(font, packet.victimName(), victimWidth), packet.victimColor(), TacticalTheme.TEXT_PRIMARY));
        if (!suffix.isBlank()) {
            result.append(Component.literal("  " + suffix)
                    .withStyle(Style.EMPTY.withColor(TacticalTheme.TEXT_SECONDARY & 0x00FFFFFF)));
        }
        return result;
    }

    static String suffix(KillFeedPacket packet) {
        return switch (packet.cause()) {
            case BLEEDING -> "[кровотечение]";
            case FIRE -> "[огонь]";
            case FALL -> "[падение]";
            case LAVA -> "[лава]";
            case ZONE -> "[зона]";
            case NONE -> packet.weaponDisplayName().isBlank() ? "" : "[" + packet.weaponDisplayName() + "]";
        };
    }

    static String rewardText(KillFeedPacket packet) {
        String coins = packet.awardedCoins() > 0 ? "+" + packet.awardedCoins() + " coins" : "";
        String xp = packet.awardedXp() > 0 ? "+" + packet.awardedXp() + " XP" : "";
        return coins.isBlank() ? xp : xp.isBlank() ? coins : coins + "   " + xp;
    }

    private static boolean hasReward(KillFeedPacket packet) {
        return packet.awardedCoins() > 0 || packet.awardedXp() > 0;
    }

    private static MutableComponent colored(String text, int packetColor, int fallback) {
        int color = packetColor == KillFeedPacket.NO_TEAM_COLOR ? fallback : packetColor;
        return Component.literal(text).withStyle(Style.EMPTY.withColor(color & 0x00FFFFFF));
    }

    private static String ellipsize(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "…";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
    }

    private static int withAlpha(int color, float alpha) {
        int baseAlpha = color >>> 24;
        int adjustedAlpha = Math.max(0, Math.min(255, Math.round(baseAlpha * alpha)));
        return (color & 0x00FFFFFF) | (adjustedAlpha << 24);
    }
}
