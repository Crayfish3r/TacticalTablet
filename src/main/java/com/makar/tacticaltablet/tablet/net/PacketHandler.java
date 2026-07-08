package com.makar.tacticaltablet.tablet.net;

import com.makar.tacticaltablet.airdrop.net.AirdropSmokeStatePacket;
import com.makar.tacticaltablet.clan.ClanAcceptJoinPacket;
import com.makar.tacticaltablet.clan.ClanChangeColorPacket;
import com.makar.tacticaltablet.clan.ClanCreatePacket;
import com.makar.tacticaltablet.clan.ClanDisbandPacket;
import com.makar.tacticaltablet.clan.ClanJoinRequestPacket;
import com.makar.tacticaltablet.clan.ClanKickMemberPacket;
import com.makar.tacticaltablet.clan.ClanLeavePacket;
import com.makar.tacticaltablet.clan.ClanListPacket;
import com.makar.tacticaltablet.clan.ClanRejectJoinPacket;
import com.makar.tacticaltablet.prefix.PrefixListPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class PacketHandler {

    private static final String VERSION = "29";

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
                VoteMapPacket.class,
                VoteMapPacket::encode,
                VoteMapPacket::new,
                VoteMapPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++,
                MapVoteStatePacket.class,
                MapVoteStatePacket::encode,
                MapVoteStatePacket::new,
                MapVoteStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++,
                SetCompetitivePacket.class,
                SetCompetitivePacket::encode,
                SetCompetitivePacket::new,
                SetCompetitivePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++,
                SetClanWarPacket.class,
                SetClanWarPacket::encode,
                SetClanWarPacket::new,
                SetClanWarPacket::handle,
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

        INSTANCE.registerMessage(id++,
                TrackerWatchPacket.class,
                TrackerWatchPacket::encode,
                TrackerWatchPacket::new,
                TrackerWatchPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++,
                AirdropSmokeStatePacket.class,
                AirdropSmokeStatePacket::encode,
                AirdropSmokeStatePacket::new,
                AirdropSmokeStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++,
                DeathScreenPacket.class,
                DeathScreenPacket::encode,
                DeathScreenPacket::new,
                DeathScreenPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++,
                SpectatorCameraSwitchPacket.class,
                SpectatorCameraSwitchPacket::encode,
                SpectatorCameraSwitchPacket::new,
                SpectatorCameraSwitchPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++,
                SpectatorCameraLockStatePacket.class,
                SpectatorCameraLockStatePacket::encode,
                SpectatorCameraLockStatePacket::new,
                SpectatorCameraLockStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++,
                ClanListPacket.class,
                ClanListPacket::encode,
                ClanListPacket::new,
                ClanListPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        INSTANCE.registerMessage(id++,
                ClanCreatePacket.class,
                ClanCreatePacket::encode,
                ClanCreatePacket::new,
                ClanCreatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++,
                ClanJoinRequestPacket.class,
                ClanJoinRequestPacket::encode,
                ClanJoinRequestPacket::new,
                ClanJoinRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++,
                ClanAcceptJoinPacket.class,
                ClanAcceptJoinPacket::encode,
                ClanAcceptJoinPacket::new,
                ClanAcceptJoinPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++,
                ClanLeavePacket.class,
                ClanLeavePacket::encode,
                ClanLeavePacket::new,
                ClanLeavePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++,
                ClanDisbandPacket.class,
                ClanDisbandPacket::encode,
                ClanDisbandPacket::new,
                ClanDisbandPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++,
                ClanRejectJoinPacket.class,
                ClanRejectJoinPacket::encode,
                ClanRejectJoinPacket::new,
                ClanRejectJoinPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++,
                ClanKickMemberPacket.class,
                ClanKickMemberPacket::encode,
                ClanKickMemberPacket::new,
                ClanKickMemberPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++,
                ClanChangeColorPacket.class,
                ClanChangeColorPacket::encode,
                ClanChangeColorPacket::new,
                ClanChangeColorPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        INSTANCE.registerMessage(id++,
                PrefixListPacket.class,
                PrefixListPacket::encode,
                PrefixListPacket::new,
                PrefixListPacket::handle,
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
