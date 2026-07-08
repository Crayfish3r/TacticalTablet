package com.makar.tacticaltablet.airdrop.client;

import com.makar.tacticaltablet.airdrop.net.AirdropSmokeStatePacket;
import com.makar.tacticaltablet.core.ModParticles;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TacticalTabletMod.MODID, value = Dist.CLIENT)
public final class AirdropSmokeClientState {
    private static final double EMIT_DISTANCE_SQ = 96.0D * 96.0D;
    private static final int EMIT_INTERVAL_TICKS = 2;
    private static final int PARTICLES_PER_EMISSION = 2;

    private static boolean active;
    private static ResourceLocation dimension;
    private static BlockPos pos = BlockPos.ZERO;
    private static int ticker;

    private AirdropSmokeClientState() {
    }

    public static void handle(AirdropSmokeStatePacket packet) {
        active = packet.active();
        dimension = packet.dimension();
        pos = packet.pos().immutable();
        ticker = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) return;

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || dimension == null) return;
        if (!level.dimension().location().equals(dimension)) return;
        if (minecraft.player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D) > EMIT_DISTANCE_SQ) {
            return;
        }

        ticker++;
        if (ticker < EMIT_INTERVAL_TICKS) return;
        ticker = 0;

        for (int i = 0; i < PARTICLES_PER_EMISSION; i++) {
            double x = pos.getX() + 0.5D + (level.random.nextDouble() - 0.5D) * 0.55D;
            double y = pos.getY() + 1.0D + level.random.nextDouble() * 0.35D;
            double z = pos.getZ() + 0.5D + (level.random.nextDouble() - 0.5D) * 0.55D;
            double velocityX = (level.random.nextDouble() - 0.5D) * 0.012D;
            double velocityY = 0.105D + level.random.nextDouble() * 0.035D;
            double velocityZ = (level.random.nextDouble() - 0.5D) * 0.012D;

            level.addParticle(ModParticles.AIRDROP_SMOKE.get(), x, y, z, velocityX, velocityY, velocityZ);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
        dimension = null;
        ticker = 0;
    }
}
