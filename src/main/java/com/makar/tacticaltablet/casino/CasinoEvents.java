package com.makar.tacticaltablet.casino;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.game.GameStateManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID)
public final class CasinoEvents {
    private static int cleanupTicks;

    private CasinoEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        handleInteraction(event, event.getTarget());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        handleInteraction(event, event.getTarget());
    }

    private static void handleInteraction(PlayerInteractEvent event, Entity target) {
        if (!isCasinoNpc(target) || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        CasinoSessionManager.openFromNpc(player);
    }

    public static boolean isCasinoNpc(Entity entity) {
        return entity instanceof Villager
                && entity.hasCustomName()
                && CasinoSessionManager.NPC_NAME.equals(entity.getCustomName().getString())
                && entity.level().dimension().equals(GameStateManager.LOBBY_DIMENSION);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) CasinoSessionManager.onLogin(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) CasinoSessionManager.onLogout(player);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++cleanupTicks < 20) return;
        cleanupTicks = 0;
        CasinoSessionManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CasinoSessionManager.clear(event.getServer());
    }
}
