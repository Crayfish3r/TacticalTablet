package com.makar.tacticaltablet.client;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.tablet.client.TabletClientState;
import com.makar.tacticaltablet.tablet.client.GuiTextureRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, value = Dist.CLIENT)
public final class LivesHudOverlay {

    private static final ResourceLocation HEART_TEXTURE =
            new ResourceLocation(TacticalTabletMod.MODID, "textures/gui/heart.png");
    private static final ResourceLocation PLAYERS_TEXTURE =
            new ResourceLocation(TacticalTabletMod.MODID, "textures/gui/players_count.png");

    private static final int HEART_SIZE = 16;
    private static final int HEART_TEXTURE_SIZE = 16;
    private static final int PLAYERS_SIZE = 16;
    private static final int PLAYERS_TEXTURE_SIZE = 16;
    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int SIDE_PADDING = 6;
    private static final int COUNTER_GAP = 10;
    private static final int TEXT_OFFSET_X = 19;
    private static final int TEXT_OFFSET_Y = 5;

    private LivesHudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderHotbar(RenderGuiOverlayEvent.Post event) {
        if (!VanillaGuiOverlay.HOTBAR.id().equals(event.getOverlay().id())) return;

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        if (player == null || player.isSpectator() || minecraft.options.hideGui) return;
        if (minecraft.screen != null) return;
        if (!TabletClientState.isGameRunning()) return;

        int lives = TabletClientState.getLives();
        if (lives <= 0) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = event.getWindow().getGuiScaledWidth();
        int screenHeight = event.getWindow().getGuiScaledHeight();

        int hotbarRight = screenWidth / 2 + HOTBAR_WIDTH / 2;
        int y = screenHeight - HOTBAR_HEIGHT + (HOTBAR_HEIGHT - HEART_SIZE) / 2;
        String livesText = "X" + lives;

        int alivePlayers = TabletClientState.getAlivePlayers();
        int remainingLivesTotal = TabletClientState.getRemainingLivesTotal();
        String playersText = alivePlayers > 0 ? "X" + alivePlayers + " (" + remainingLivesTotal + ")" : "";
        int livesWidth = HEART_SIZE + 3 + minecraft.font.width(livesText);
        int playersWidth = playersText.isEmpty() ? 0 : PLAYERS_SIZE + 3 + minecraft.font.width(playersText);
        int totalWidth = livesWidth + (playersWidth > 0 ? COUNTER_GAP + playersWidth : 0);
        int x = Math.min(hotbarRight + SIDE_PADDING, screenWidth - totalWidth - 2);
        x = Math.max(2, x);

        GuiTextureRenderer.blitWithAlpha(
                graphics,
                HEART_TEXTURE,
                x,
                y,
                HEART_SIZE,
                HEART_SIZE,
                HEART_TEXTURE_SIZE,
                HEART_TEXTURE_SIZE
        );

        graphics.drawString(
                minecraft.font,
                livesText,
                x + TEXT_OFFSET_X,
                y + TEXT_OFFSET_Y,
                0xFFFFFFFF,
                true
        );

        if (playersText.isEmpty()) return;

        int playersX = x + livesWidth + COUNTER_GAP;

        GuiTextureRenderer.blitWithAlpha(
                graphics,
                PLAYERS_TEXTURE,
                playersX,
                y,
                PLAYERS_SIZE,
                PLAYERS_SIZE,
                PLAYERS_TEXTURE_SIZE,
                PLAYERS_TEXTURE_SIZE
        );

        graphics.drawString(
                minecraft.font,
                playersText,
                playersX + TEXT_OFFSET_X,
                y + TEXT_OFFSET_Y,
                0xFFFFFFFF,
                true
        );
    }
}

