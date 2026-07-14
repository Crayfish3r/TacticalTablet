package com.makar.tacticaltablet.tablet.net;

import org.junit.jupiter.api.Test;
import net.minecraftforge.network.NetworkDirection;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacketRegistryTest {

    @Test
    void registryPreservesAllProtocol32IdsAndDirections() {
        var entries = PacketProtocol.entries();
        Map<Class<?>, PacketProtocol.Entry> map = entries.stream()
                .collect(java.util.stream.Collectors.toMap(PacketProtocol.Entry::packetClass, entry -> entry));

        assertEquals(28, entries.size());
        assertEquals(28, new HashSet<>(entries.stream().map(PacketProtocol.Entry::id).toList()).size());
        assertEquals(IntStream.range(0, 28).boxed().toList(), entries.stream().map(PacketProtocol.Entry::id).toList());
        assertEquals(List.of(
                TabletPacket.class, TabletStatePacket.class, VoteModePacket.class, JoinTeamPacket.class, VoteMapPacket.class,
                MapVoteStatePacket.class, SetCompetitivePacket.class, SetClanWarPacket.class, ContractSelectionStatePacket.class,
                ContractSelectTargetPacket.class, ContractOpenTrackerPacket.class, ContractTrackerStatePacket.class, TrackerWatchPacket.class,
                com.makar.tacticaltablet.airdrop.net.AirdropSmokeStatePacket.class, com.makar.tacticaltablet.airdrop.net.AirdropNoticePacket.class,
                DeathScreenPacket.class, SpectatorCameraSwitchPacket.class, SpectatorCameraLockStatePacket.class,
                com.makar.tacticaltablet.clan.ClanListPacket.class, com.makar.tacticaltablet.clan.ClanCreatePacket.class,
                com.makar.tacticaltablet.clan.ClanJoinRequestPacket.class, com.makar.tacticaltablet.clan.ClanAcceptJoinPacket.class,
                com.makar.tacticaltablet.clan.ClanLeavePacket.class, com.makar.tacticaltablet.clan.ClanDisbandPacket.class,
                com.makar.tacticaltablet.clan.ClanRejectJoinPacket.class, com.makar.tacticaltablet.clan.ClanKickMemberPacket.class,
                com.makar.tacticaltablet.clan.ClanChangeColorPacket.class, com.makar.tacticaltablet.prefix.PrefixListPacket.class
        ), entries.stream().map(PacketProtocol.Entry::packetClass).toList());
        assertEquals(19, map.get(com.makar.tacticaltablet.clan.ClanCreatePacket.class).id());
        assertEquals(18, map.get(com.makar.tacticaltablet.clan.ClanListPacket.class).id());
        assertEquals(NetworkDirection.PLAY_TO_SERVER, map.get(com.makar.tacticaltablet.clan.ClanCreatePacket.class).direction());
        assertEquals(NetworkDirection.PLAY_TO_CLIENT, map.get(com.makar.tacticaltablet.clan.ClanListPacket.class).direction());
        assertEquals(List.of(
                NetworkDirection.PLAY_TO_SERVER, NetworkDirection.PLAY_TO_CLIENT, NetworkDirection.PLAY_TO_SERVER,
                NetworkDirection.PLAY_TO_SERVER, NetworkDirection.PLAY_TO_SERVER, NetworkDirection.PLAY_TO_CLIENT,
                NetworkDirection.PLAY_TO_SERVER, NetworkDirection.PLAY_TO_SERVER, NetworkDirection.PLAY_TO_CLIENT,
                NetworkDirection.PLAY_TO_SERVER, NetworkDirection.PLAY_TO_SERVER, NetworkDirection.PLAY_TO_CLIENT,
                NetworkDirection.PLAY_TO_SERVER, NetworkDirection.PLAY_TO_CLIENT, NetworkDirection.PLAY_TO_CLIENT,
                NetworkDirection.PLAY_TO_CLIENT, NetworkDirection.PLAY_TO_SERVER, NetworkDirection.PLAY_TO_CLIENT,
                NetworkDirection.PLAY_TO_CLIENT, NetworkDirection.PLAY_TO_SERVER, NetworkDirection.PLAY_TO_SERVER,
                NetworkDirection.PLAY_TO_SERVER, NetworkDirection.PLAY_TO_SERVER, NetworkDirection.PLAY_TO_SERVER,
                NetworkDirection.PLAY_TO_SERVER, NetworkDirection.PLAY_TO_SERVER, NetworkDirection.PLAY_TO_SERVER,
                NetworkDirection.PLAY_TO_CLIENT
        ), entries.stream().map(PacketProtocol.Entry::direction).toList());
        PacketProtocol.verify();
    }

    @Test
    void publicPacketIdConstantsMatchProtocolMetadata() {
        List<Integer> constants = List.of(
                PacketHandler.TABLET, PacketHandler.TABLET_STATE, PacketHandler.VOTE_MODE, PacketHandler.JOIN_TEAM,
                PacketHandler.VOTE_MAP, PacketHandler.MAP_VOTE_STATE, PacketHandler.SET_COMPETITIVE,
                PacketHandler.SET_CLAN_WAR, PacketHandler.CONTRACT_SELECTION_STATE, PacketHandler.CONTRACT_SELECT_TARGET,
                PacketHandler.CONTRACT_OPEN_TRACKER, PacketHandler.CONTRACT_TRACKER_STATE, PacketHandler.TRACKER_WATCH,
                PacketHandler.AIRDROP_SMOKE_STATE, PacketHandler.AIRDROP_NOTICE, PacketHandler.DEATH_SCREEN,
                PacketHandler.SPECTATOR_CAMERA_SWITCH, PacketHandler.SPECTATOR_CAMERA_LOCK_STATE, PacketHandler.CLAN_LIST,
                PacketHandler.CLAN_CREATE, PacketHandler.CLAN_JOIN_REQUEST, PacketHandler.CLAN_ACCEPT_JOIN,
                PacketHandler.CLAN_LEAVE, PacketHandler.CLAN_DISBAND, PacketHandler.CLAN_REJECT_JOIN,
                PacketHandler.CLAN_KICK_MEMBER, PacketHandler.CLAN_CHANGE_COLOR, PacketHandler.PREFIX_LIST
        );

        assertEquals(PacketProtocol.entries().stream().map(PacketProtocol.Entry::id).toList(), constants);
    }
}
