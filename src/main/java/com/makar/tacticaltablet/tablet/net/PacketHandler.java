package com.makar.tacticaltablet.tablet.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class PacketHandler {

    private static final String VERSION = "11";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("tacticaltablet", "main"),
            () -> VERSION,
            VERSION::equals,
            VERSION::equals
    );

    private static int id = 0;
    private static boolean registered = false;

    public static synchronized void register() {
        if (registered) return;
        registered = true;

        INSTANCE.registerMessage(id++,
                TabletPacket.class,
                TabletPacket::encode,
                TabletPacket::new,
                TabletPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++,
                TabletStatePacket.class,
                TabletStatePacket::encode,
                TabletStatePacket::new,
                TabletStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++,
                VoteModePacket.class,
                VoteModePacket::encode,
                VoteModePacket::new,
                VoteModePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++,
                JoinTeamPacket.class,
                JoinTeamPacket::encode,
                JoinTeamPacket::new,
                JoinTeamPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++,
                ContractSelectionStatePacket.class,
                ContractSelectionStatePacket::encode,
                ContractSelectionStatePacket::new,
                ContractSelectionStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++,
                ContractSelectTargetPacket.class,
                ContractSelectTargetPacket::encode,
                ContractSelectTargetPacket::new,
                ContractSelectTargetPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++,
                ContractOpenTrackerPacket.class,
                ContractOpenTrackerPacket::encode,
                ContractOpenTrackerPacket::new,
                ContractOpenTrackerPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++,
                ContractTrackerStatePacket.class,
                ContractTrackerStatePacket::encode,
                ContractTrackerStatePacket::new,
                ContractTrackerStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static boolean isRegistered() {
        return registered;
    }

    public static void sendToServer(Object msg) {
        INSTANCE.sendToServer(msg);
    }

    public static void sendToPlayer(ServerPlayer player, Object msg) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
