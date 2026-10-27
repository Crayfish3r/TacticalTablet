package com.makar.tacticaltablet.client;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.tablet.client.ArenaClientState;
import com.makar.tacticaltablet.tablet.client.TabletClientState;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, value = Dist.CLIENT)
public final class ArenaHudOverlay {
    private ArenaHudOverlay() { }
    @SubscribeEvent public static void render(RenderGuiOverlayEvent.Post event) {
        if (!VanillaGuiOverlay.HOTBAR.id().equals(event.getOverlay().id())) return;
        var mc = Minecraft.getInstance(); var state = ArenaClientState.snapshot();
        if (mc.player == null || mc.options.hideGui || mc.screen != null || !state.active()
                || !TabletClientState.isGameRunning()) return;
        var own = state.rows().stream().filter(r -> r.playerId().equals(mc.player.getUUID())).findFirst();
        if (own.isEmpty()) return;
        var g = event.getGuiGraphics();
        int x = 8, y = 8, width = 188, count = Math.min(5, state.rows().size());
        TacticalUi.drawCutCornerBorder(g, x, y, width, 38 + count * 13, 4, 1,
                ExternalUiTheme.ACCENT, ExternalUiTheme.SURFACE);
        g.drawString(mc.font, "Арена • " + state.gameNumber() + "/" + state.gamesPerSet(), x + 8, y + 7,
                ExternalUiTheme.TEXT_PRIMARY, false);
        g.drawString(mc.font, "Ваши очки: " + own.get().score(), x + 8, y + 22, ExternalUiTheme.WARNING, false);
        for (int i = 0; i < count; i++) {
            var row = state.rows().get(i);
            String score = Integer.toString(row.score());
            String name = mc.font.plainSubstrByWidth((i + 1) + ". " + row.name(),
                    Math.max(40, width - 24 - mc.font.width(score) - 12));
            g.drawString(mc.font, name, x + 8, y + 38 + i * 13, ExternalUiTheme.TEXT_SECONDARY, false);
            g.drawString(mc.font, score, x + width - 8 - mc.font.width(score), y + 38 + i * 13,
                    ExternalUiTheme.ACCENT, false);
        }
    }
}
