package com.makar.utopia.client;

import com.makar.utopia.Utopia;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** The screen owns the held-item session and the first-person animation. */
@Mod.EventBusSubscriber(modid = Utopia.MOD_ID, value = Dist.CLIENT)
public final class TabletPrototypeClient {
    private static final TabletAnimationController MOTION = new TabletAnimationController(0.65, 0.45);
    private static InteractionHand hand;
    private static ClientLevel level;
    private static int selectedSlot;
    private static CameraType previousCamera;
    private static boolean previousHideGui;

    private TabletPrototypeClient() { }

    @SubscribeEvent
    public static void use(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isAlive() || mc.player.isSpectator() || mc.player.isUsingItem()
                || mc.screen != null || TabletPrototypeModel.INSTANCE.model() == null) return;
        InteractionHand candidate = tablet(mc.player.getMainHandItem()) ? InteractionHand.MAIN_HAND
                : tablet(mc.player.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
        if (candidate == null) return;

        event.setCanceled(true);
        event.setSwingHand(false);
        pressUse();
        if (!MOTION.active()) return;
        hand = candidate;
        selectedSlot = mc.player.getInventory().selected;
        level = mc.level;
        previousCamera = mc.options.getCameraType();
        previousHideGui = mc.options.hideGui;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.hideGui = false;
        mc.setScreen(new TabletPrototypeScreen());
    }

    static void pressUse() { MOTION.pressUse(System.nanoTime()); }
    static void close() { MOTION.requestClose(System.nanoTime()); }

    private static boolean tablet(ItemStack stack) {
        return stack.is(Utopia.TABLET.get());
    }

    private static boolean valid(Minecraft mc) {
        return mc.level != null && mc.level == level && mc.player != null && mc.player.isAlive()
                && !mc.player.isSpectator() && hand != null && tablet(mc.player.getItemInHand(hand))
                && (hand != InteractionHand.MAIN_HAND || mc.player.getInventory().selected == selectedSlot);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        InputConstants.Key key = mc.options.keyUse.getKey();
        long window = mc.getWindow().getWindow();
        if (!mc.isWindowActive()
                || key.getType() == InputConstants.Type.MOUSE && GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_RELEASE
                || key.getType() == InputConstants.Type.KEYSYM && !InputConstants.isKeyDown(window, key.getValue())) {
            MOTION.releaseUse();
        }
        if (!(mc.screen instanceof TabletPrototypeScreen)) return;
        MOTION.update(System.nanoTime());
        if (!MOTION.active() || !mc.isWindowActive() || !valid(mc)) reset();
    }

    @SubscribeEvent
    public static void key(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_RELEASE
                && Minecraft.getInstance().options.keyUse.matches(event.getKey(), event.getScanCode())) MOTION.releaseUse();
    }

    @SubscribeEvent
    public static void renderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!MOTION.active() || !valid(mc) || !(mc.screen instanceof TabletPrototypeScreen)) return;
        long now = System.nanoTime();
        MOTION.update(now);
        if (!MOTION.active()) return;
        event.setCanceled(true);
        // Vanilla can suppress the offhand render event, for example for a charged crossbow.
        // The main hand still has a render pass while the player is not actively using an item.
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            boolean right = (hand == InteractionHand.MAIN_HAND) == (mc.player.getMainArm() == HumanoidArm.RIGHT);
            TabletPrototypeRenderer.renderFirstPerson(event.getPoseStack(), event.getMultiBufferSource(),
                    event.getPackedLight(), right, MOTION.clip(), MOTION.seconds(now), event.getPartialTick());
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        TabletMapState.clear();
        reset();
    }

    @SubscribeEvent
    public static void shutdown(GameShuttingDownEvent event) {
        reset();
    }

    static void resetState() {
        MOTION.reset();
        hand = null;
        level = null;
        if (previousCamera != null) {
            var options = Minecraft.getInstance().options;
            options.setCameraType(previousCamera);
            options.hideGui = previousHideGui;
            previousCamera = null;
        }
    }

    static void reset() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TabletPrototypeScreen) mc.setScreen(null);
        else resetState();
    }
}
