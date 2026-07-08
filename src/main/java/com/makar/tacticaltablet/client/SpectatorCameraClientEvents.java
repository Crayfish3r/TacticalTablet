package com.makar.tacticaltablet.client;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.tablet.net.PacketHandler;
import com.makar.tacticaltablet.tablet.net.SpectatorCameraSwitchPacket;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public final class SpectatorCameraClientEvents {

    private static final String CATEGORY = "key.categories.tacticaltablet";
    private static final String LOCK_HINT =
            "Режим наблюдателя · переключение между игроками: PageUp / PageDown";
    private static final KeyMapping NEXT_TARGET = new KeyMapping(
            "key.tacticaltablet.spectator_next",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_DOWN,
            CATEGORY
    );
    private static final KeyMapping PREVIOUS_TARGET = new KeyMapping(
            "key.tacticaltablet.spectator_previous",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_UP,
            CATEGORY
    );

    private SpectatorCameraClientEvents() {
    }

    @Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBusEvents {

        private ModBusEvents() {
        }

        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(NEXT_TARGET);
            event.register(PREVIOUS_TARGET);
        }
    }

    @Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, value = Dist.CLIENT)
    public static final class ForgeBusEvents {

        private ForgeBusEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            if (!isLockedGameplay()) {
                return;
            }

            while (NEXT_TARGET.consumeClick()) {
                PacketHandler.sendToServer(new SpectatorCameraSwitchPacket(1));
            }

            while (PREVIOUS_TARGET.consumeClick()) {
                PacketHandler.sendToServer(new SpectatorCameraSwitchPacket(-1));
            }

            Minecraft minecraft = Minecraft.getInstance();
            for (KeyMapping hotbarKey : minecraft.options.keyHotbarSlots) {
                while (hotbarKey.consumeClick()) {
                    // Consume vanilla spectator hotbar selection while the server lock is active.
                }
            }
        }

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            if (!isLockedGameplay()) return;
            if (event.getAction() != GLFW.GLFW_PRESS && event.getAction() != GLFW.GLFW_REPEAT) return;
            if (!isNumberKey(event.getKey())) return;

            if (event.isCancelable()) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onRenderHotbar(RenderGuiOverlayEvent.Post event) {
            if (!VanillaGuiOverlay.HOTBAR.id().equals(event.getOverlay().id())) return;
            if (!isLockedGameplay()) return;

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.options.hideGui) return;

            GuiGraphics graphics = event.getGuiGraphics();
            int screenWidth = event.getWindow().getGuiScaledWidth();
            int screenHeight = event.getWindow().getGuiScaledHeight();
            int textWidth = minecraft.font.width(LOCK_HINT);
            int x = screenWidth / 2;
            int y = screenHeight - 40;
            int paddingX = 6;
            int paddingY = 4;

            graphics.fill(
                    x - textWidth / 2 - paddingX,
                    y - paddingY,
                    x + textWidth / 2 + paddingX,
                    y + minecraft.font.lineHeight + paddingY,
                    0x66000000
            );
            graphics.drawCenteredString(minecraft.font, LOCK_HINT, x, y, 0xFFE6E6E6);
        }
    }

    private static boolean isLockedGameplay() {
        Minecraft minecraft = Minecraft.getInstance();
        return SpectatorCameraClientState.isLocked()
                && minecraft.player != null
                && minecraft.player.isSpectator()
                && minecraft.screen == null;
    }

    private static boolean isNumberKey(int key) {
        return key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9;
    }
}
