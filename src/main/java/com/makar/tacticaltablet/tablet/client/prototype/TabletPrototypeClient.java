package com.makar.tacticaltablet.tablet.client.prototype;

import com.makar.tacticaltablet.client.DeathScreenOverlay;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.tablet.TacticalTabletItem;
import com.makar.tacticaltablet.game.MatchPhase;
import com.makar.tacticaltablet.tablet.client.TabletClientState;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Entire lifecycle runs on the client thread. No server action accompanies visual use. */
@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, value = Dist.CLIENT)
public final class TabletPrototypeClient {
    private static final TabletAnimationController MOTION = new TabletAnimationController(0.65, 0.45);
    private static InteractionHand hand;
    private static ItemStack held = ItemStack.EMPTY;
    private static Object level;
    private static int selectedSlot;
    private static MatchPhase lastPhase;
    private static boolean mapOpenSeen, mapOpenPending;

    private TabletPrototypeClient() { }
    private static long now() { return System.nanoTime(); }

    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        InteractionHand candidate = tablet(mc.player.getMainHandItem()) ? InteractionHand.MAIN_HAND
                : tablet(mc.player.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
        if (candidate == null) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (mc.screen != null || !mc.player.isAlive() || DeathScreenOverlay.isActive()) return;
        if (!MOTION.active()) {
            rememberHeldTablet(mc, candidate);
        }
        pressUse();
        if (MOTION.active() && !(mc.screen instanceof TabletPrototypeScreen)) mc.setScreen(new TabletPrototypeScreen());
    }

    static void pressUse() { MOTION.pressUse(now()); }
    static void close() { MOTION.requestClose(now()); }
    static boolean isOpen() { return MOTION.state() == TabletAnimationController.State.OPEN; }

    private static void rememberHeldTablet(Minecraft mc, InteractionHand candidate) {
        hand = candidate;
        held = mc.player.getItemInHand(hand).copy();
        selectedSlot = mc.player.getInventory().selected;
        level = mc.level;
    }

    /** One server opening signal per map vote; ordinary state packets never navigate or reopen. */
    public static void onMapVoteState(boolean active, boolean openScreen, long snapshotRequestId) {
        if (!active) { mapOpenSeen = false; mapOpenPending = false; }
        else if (openScreen && !mapOpenSeen) { mapOpenSeen = true; mapOpenPending = true; }
        if (Minecraft.getInstance().screen instanceof TabletPrototypeScreen screen && screen.shell() != null)
            screen.shell().acceptMapSnapshot(snapshotRequestId);
    }

    private static void openVotingOnce(Minecraft mc) {
        if (MOTION.active() || mc.screen != null || mc.level == null || mc.player == null
                || !mc.player.isAlive() || DeathScreenOverlay.isActive() || !mc.isWindowActive()) return;
        InteractionHand candidate = tablet(mc.player.getMainHandItem()) ? InteractionHand.MAIN_HAND
                : tablet(mc.player.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
        if (candidate == null) return;
        rememberHeldTablet(mc, candidate);
        pressUse();
        if (MOTION.active()) mc.setScreen(new TabletPrototypeScreen());
    }

    private static boolean tablet(ItemStack stack) { return stack.getItem() instanceof TacticalTabletItem; }
    private static boolean valid(Minecraft mc) {
        return mc.level != null && mc.level == level
                && mc.player != null && mc.player.isAlive() && !DeathScreenOverlay.isActive()
                && hand != null && ItemStack.isSameItem(mc.player.getItemInHand(hand), held)
                && tablet(mc.player.getItemInHand(hand))
                && (hand != InteractionHand.MAIN_HAND || mc.player.getInventory().selected == selectedSlot);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        releaseIfUp(mc);
        TabletArenaTeamsPage.observeConnection();
        if (MOTION.active() && (!valid(mc) || !mc.isWindowActive()
                || !(mc.screen instanceof TabletPrototypeScreen))) reset();
        MOTION.update(now());
        if (!MOTION.active() && mc.screen instanceof TabletPrototypeScreen) mc.setScreen(null);
        if (TabletClientState.hasMatchSetup()) {
            MatchPhase phase = TabletClientState.getMatchPhase();
            boolean formatStarted = phase == MatchPhase.VOTING && lastPhase != phase;
            if (lastPhase == MatchPhase.MAP_VOTING && phase != lastPhase) {
                mapOpenSeen = false; mapOpenPending = false;
            }
            lastPhase = phase;
            if (formatStarted || mapOpenPending && phase == MatchPhase.MAP_VOTING) {
                mapOpenPending = false;
                openVotingOnce(mc); // Consumed even when another screen/held item prevents opening.
            }
        }
    }

    private static void releaseIfUp(Minecraft mc) {
        InputConstants.Key key = mc.options.keyUse.getKey();
        long window = mc.getWindow().getWindow();
        if (!mc.isWindowActive()
                || key.getType() == InputConstants.Type.MOUSE && GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_RELEASE
                || key.getType() == InputConstants.Type.KEYSYM && !InputConstants.isKeyDown(window, key.getValue())) MOTION.releaseUse();
    }

    @SubscribeEvent public static void key(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_RELEASE && Minecraft.getInstance().options.keyUse.matches(event.getKey(), event.getScanCode()))
            MOTION.releaseUse();
    }
    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Post event) {
        if (event.getAction() == GLFW.GLFW_RELEASE && Minecraft.getInstance().options.keyUse.matchesMouse(event.getButton()))
            MOTION.releaseUse();
    }

    @SubscribeEvent public static void renderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!MOTION.active()) return;
        if (!valid(mc)) { reset(); return; }
        long time = now();
        MOTION.update(time);
        // Hide both vanilla hands during the visual session; render exactly once for its owner.
        event.setCanceled(true);
        if (MOTION.active() && event.getHand() == hand) {
            boolean right = (hand == InteractionHand.MAIN_HAND)
                    == (mc.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);
            TabletPrototypeRenderer.renderFirstPerson(event.getPoseStack(), event.getMultiBufferSource(),
                    event.getPackedLight(), right, MOTION.clip(), MOTION.seconds(time));
        }
    }

    public static void screenOpening(Screen screen) {
        if (MOTION.active() && !(screen instanceof TabletPrototypeScreen)) resetState();
    }
    @SubscribeEvent public static void opening(ScreenEvent.Opening event) {
        screenOpening(event.getNewScreen());
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        TabletClassesClient.reset(); TabletArenaTeamsPage.reset();
        lastPhase = null; mapOpenSeen = false; mapOpenPending = false;
        reset(); TabletDisplayTarget.release();
    }
    @SubscribeEvent public static void shutdown(net.minecraftforge.event.GameShuttingDownEvent event) { TabletDisplayTarget.release(); }
    static void resetState() {
        MOTION.reset();
        hand = null;
        held = ItemStack.EMPTY;
        level = null;
        TabletDisplayProjection.clear();
    }
    public static void reset() {
        resetState();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TabletPrototypeScreen) mc.setScreen(null);
    }
}
