package com.makar.tacticaltablet.client;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.net.KillFeedPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, value = Dist.CLIENT)
public final class KillFeedOverlay {
    private static final int CARD_WIDTH = 190;
    private static final int CARD_HEIGHT = 24;
    private static final int GAP = 3;

    private KillFeedOverlay() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) KillFeedClientState.tick();
    }

    @SubscribeEvent
    public static void onRenderHotbar(RenderGuiOverlayEvent.Post event) {
        if (!VanillaGuiOverlay.HOTBAR.id().equals(event.getOverlay().id())) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int x = event.getWindow().getGuiScaledWidth() - CARD_WIDTH - 8;
        int y = 8;
        for (KillFeedClientState.Entry entry : KillFeedClientState.entries()) {
            float alpha = entry.alpha();
            int background = withAlpha(TacticalTheme.SURFACE, alpha * 0.90F);
            int border = withAlpha(TacticalTheme.ACCENT, alpha);
            int text = withAlpha(TacticalTheme.TEXT_PRIMARY, alpha);
            TacticalUi.drawCutCornerBorder(graphics, x, y, CARD_WIDTH, CARD_HEIGHT,
                    TacticalTheme.CORNER_CUT, 1, border, background);
            graphics.drawString(minecraft.font, format(entry.packet()), x + 6, y + 4, text, false);
            if (!entry.packet().weaponId().isBlank()) {
                graphics.drawString(minecraft.font, entry.packet().weaponId(), x + 6,
                        y + 4 + minecraft.font.lineHeight, withAlpha(TacticalTheme.TEXT_SECONDARY, alpha), false);
            }
            y += CARD_HEIGHT + GAP;
        }
    }

    static String format(KillFeedPacket packet) {
        String base = packet.killerName().isBlank()
                ? packet.victimName()
                : packet.killerName() + " \u2192 " + packet.victimName();
        return switch (packet.cause()) {
            case BLEEDING -> base + " [\u043a\u0440\u043e\u0432\u043e\u0442\u0435\u0447\u0435\u043d\u0438\u0435]";
            case FIRE -> base + " [\u043e\u0433\u043e\u043d\u044c]";
            case FALL -> base + " [\u043f\u0430\u0434\u0435\u043d\u0438\u0435]";
            case NONE -> base;
        };
    }

    private static int withAlpha(int color, float alpha) {
        int baseAlpha = color >>> 24;
        int adjustedAlpha = Math.max(0, Math.min(255, Math.round(baseAlpha * alpha)));
        return (color & 0x00FFFFFF) | (adjustedAlpha << 24);
    }
}
