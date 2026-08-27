package com.makar.tacticaltablet.integration.moderndamage.client;

import com.makar.tacticaltablet.airdrop.client.AirdropNoticeClientState;
import com.makar.tacticaltablet.client.HudAnchorManager;
import com.makar.tacticaltablet.client.KillFeedClientState;
import com.makar.tacticaltablet.client.ExternalUiTheme;
import com.makar.tacticaltablet.core.TacticalTabletClientConfig;
import com.makar.tacticaltablet.tablet.client.TabletClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/** Manually registered only on a physical client after the exact MDC version check. */
final class ModernDamageStaminaOverlay {
    static final int BASE_WIDTH = 92;
    static final int BASE_ROW_HEIGHT = 4;
    private static final int BASE_GAP = 3;
    private static final ModernDamageClientAccessV1032.Snapshot PREVIEW =
            new ModernDamageClientAccessV1032.Snapshot(true, 0.72F, true, 0.43F);
    private final ModernDamageClientAccessV1032 access;

    ModernDamageStaminaOverlay(ModernDamageClientAccessV1032 access) {
        this.access = access;
    }

    @SubscribeEvent
    public void onRenderHotbar(RenderGuiOverlayEvent.Post event) {
        if (!VanillaGuiOverlay.HOTBAR.id().equals(event.getOverlay().id())) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.isSpectator()
                || minecraft.options.hideGui || minecraft.screen != null
                || !TacticalTabletClientConfig.MDC_STAMINA_HUD_ENABLED.get()) return;

        access.disableStockStaminaHudIfRequired();
        ModernDamageClientAccessV1032.Snapshot snapshot = access.snapshot(minecraft.player);
        int rows = (snapshot.armsEnabled() ? 1 : 0) + (snapshot.legsEnabled() ? 1 : 0);
        if (rows == 0) return;

        renderConfigured(event.getGuiGraphics(), event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight(), snapshot, List.of());
    }

    static void renderPreview(GuiGraphics graphics, int screenWidth, int screenHeight,
                              List<HudAnchorManager.Rect> editorReservations) {
        renderConfigured(graphics, screenWidth, screenHeight, PREVIEW, editorReservations);
    }

    private static void renderConfigured(GuiGraphics graphics, int screenWidth, int screenHeight,
                                         ModernDamageClientAccessV1032.Snapshot snapshot,
                                         List<HudAnchorManager.Rect> additionalReservations) {
        int rows = (snapshot.armsEnabled() ? 1 : 0) + (snapshot.legsEnabled() ? 1 : 0);
        if (rows == 0) return;
        float scale = TacticalTabletClientConfig.MDC_STAMINA_HUD_SCALE.get().floatValue();
        float opacity = TacticalTabletClientConfig.MDC_STAMINA_HUD_OPACITY.get().floatValue();
        int baseHeight = rows * BASE_ROW_HEIGHT + Math.max(0, rows - 1) * BASE_GAP;
        int scaledWidth = Math.round(BASE_WIDTH * scale);
        int scaledHeight = Math.round(baseHeight * scale);
        HudAnchorManager.Rect anchor = HudAnchorManager.staminaBars(
                screenWidth, screenHeight, scaledWidth, scaledHeight, side(),
                TacticalTabletClientConfig.MDC_STAMINA_HUD_X_OFFSET.get(),
                TacticalTabletClientConfig.MDC_STAMINA_HUD_Y_OFFSET.get(),
                occupied(screenWidth, screenHeight, additionalReservations));

        graphics.pose().pushPose();
        graphics.pose().translate(anchor.x(), anchor.y(), 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        renderBars(graphics, snapshot, opacity);
        graphics.pose().popPose();
    }

    private static void renderBars(GuiGraphics graphics, ModernDamageClientAccessV1032.Snapshot snapshot,
                                   float opacity) {
        int y = 0;
        if (snapshot.armsEnabled()) {
            renderBar(graphics, y, snapshot.armsRatio(), opacity);
            y += BASE_ROW_HEIGHT + BASE_GAP;
        }
        if (snapshot.legsEnabled()) {
            renderBar(graphics, y, snapshot.legsRatio(), opacity);
        }
    }

    private static void renderBar(GuiGraphics graphics, int y, float ratio, float opacity) {
        int trackX = 0;
        int trackY = y;
        int trackWidth = BASE_WIDTH;
        int trackHeight = 4;
        graphics.fill(trackX, trackY, trackX + trackWidth, trackY + trackHeight,
                alpha(ExternalUiTheme.SURFACE_RAISED, opacity));
        int fill = Math.round(trackWidth * ratio);
        if (fill > 0) {
            graphics.fill(trackX, trackY, trackX + fill, trackY + trackHeight,
                    alpha(staminaColor(ratio), opacity));
        }
        graphics.fill(trackX, trackY, trackX + trackWidth, trackY + 1,
                alpha(ExternalUiTheme.BORDER, opacity));
    }

    static int staminaColor(float ratio) {
        float clamped = Math.max(0.0F, Math.min(1.0F, ratio));
        if (clamped < 0.5F) return lerpColor(ExternalUiTheme.DANGER, ExternalUiTheme.SECONDARY,
                clamped * 2.0F);
        return lerpColor(ExternalUiTheme.SECONDARY, ExternalUiTheme.PRIMARY,
                (clamped - 0.5F) * 2.0F);
    }

    private static int lerpColor(int from, int to, float amount) {
        int red = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * amount);
        int green = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * amount);
        int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int alpha(int color, float opacity) {
        int base = color >>> 24;
        int adjusted = Math.max(0, Math.min(255, Math.round(base * opacity)));
        return color & 0x00FFFFFF | adjusted << 24;
    }

    private static HudAnchorManager.Side side() {
        return switch (TacticalTabletClientConfig.MDC_STAMINA_HUD_SIDE.get()) {
            case LEFT -> HudAnchorManager.Side.LEFT;
            case RIGHT -> HudAnchorManager.Side.RIGHT;
            case AUTO -> HudAnchorManager.Side.AUTO;
        };
    }

    private static List<HudAnchorManager.Rect> occupied(int width, int height,
                                                        List<HudAnchorManager.Rect> additional) {
        List<HudAnchorManager.Rect> occupied = new ArrayList<>();
        occupied.add(new HudAnchorManager.Rect(Math.max(0, width / 2 - 101), Math.max(0, height - 70),
                Math.min(202, width), 70));
        if (!KillFeedClientState.entries().isEmpty()) {
            occupied.add(new HudAnchorManager.Rect(Math.max(0, width - 230), 4, Math.min(230, width), 112));
        }
        if (AirdropNoticeClientState.isVisible()) {
            occupied.add(HudAnchorManager.topCenter(width, height, 280, 45));
        }
        if (TabletClientState.isGameRunning() && TabletClientState.getLives() > 0) {
            occupied.add(new HudAnchorManager.Rect(width / 2 + 88, Math.max(0, height - 26),
                    Math.max(0, width / 2 - 88), 26));
        }
        occupied.addAll(additional);
        return occupied;
    }
}
